/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.viztrails

import scalikejdbc._
import scala.collection.mutable
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.Workflow
import info.vizierdb.catalog.Cell
import scalikejdbc.DBSession
import info.vizierdb.catalog.Module
import info.vizierdb.catalog.ArtifactRef
import java.util.concurrent.{ ArrayBlockingQueue, ForkJoinTask }
import scala.collection.JavaConverters._
import info.vizierdb.delta.DeltaBus
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.catalog.InputArtifactRef
import info.vizierdb.catalog.OutputArtifactRef
import java.util.concurrent.atomic.AtomicBoolean
import info.vizierdb.util.ClassLoaderUtils

class RunningWorkflow(workflow: Workflow, val classloader: ClassLoader)
  extends ForkJoinTask[Unit]
  with LazyLogging
{

  val pendingTasks = mutable.Map[Cell.Position, RunningCell]()
  val completionMessages = new ArrayBlockingQueue[Cell.Position](50)
  completionMessages.add(-1)
  var aborted = new AtomicBoolean(false)

  def abort()
  {
    cancel(true)
    aborted.set(true)
    for(task <- pendingTasks.values){
      logger.trace(s"Aborting task $task")
      task.abort() 
    }
  }

  def scheduleCell(cell: Cell, module: Module, scope: ScopeSummary)
  {
    logger.trace(s"Scheduing cell $cell; Scope:$scope")
    val executor = new RunningCell(cell, module, scope, this)
    pendingTasks.put(cell.position, executor)
    Scheduler.cellWorkers.execute(executor)
  }

  def exec: Boolean =
  {
    ClassLoaderUtils.withContextClassLoader[Boolean](classloader){
      val ret = 
        try { 
          logger.info(s"Starting execution of Workflow ${workflow.id}")
          if(workflow.aborted) {
            logger.debug(s"Aborted processing of Workflow ${workflow.id} before start")
          }

          do {
            completionMessages.take match {
              case x if x < 0 => // initial message.  Ignore
              case x => {
                val otherCompletions = new java.util.ArrayList[Cell.Position]()
                completionMessages.drainTo(otherCompletions)
                logger.trace(s"Workflow ${workflow.id}: Got completion events for ${(x +: otherCompletions.asScala).mkString(", ")}")
                for(position <- (x +: otherCompletions.asScala)){
                  logger.trace(s"Workflow ${workflow.id}: Completing cell at position $position")
                  pendingTasks.remove(position) match {
                    case Some(cellTask) => 
                      logger.info(s"Workflow ${workflow.id}: Finished executing cell ${cellTask.cell} [${cellTask.module.packageId}.${cellTask.module.commandId}]")
                      cellTask.cleanup()
                    case None => 
                      logger.info(s"Workflow ${workflow.id}: Spurious completion notification (an abort probably failed)")
                  }
                }

              }
            }

            logger.trace("Recomputing Cell States")

            updatePendingTasks()

            logger.trace(s"Waiting on pending cells: ${pendingTasks.values.map { _.cell.toString }.mkString(", ")}")        

          } while( !aborted.get && !pendingTasks.isEmpty )
          /* return */ true
        } catch {
          case e: Exception => 
            logger.error(s"Error processing workflow ${workflow.id}: $e")
            e.printStackTrace()
            /* return */ false
        }
      logger.info(s"Done processing Workflow ${workflow.id}")
      return ret
    }
  }

  def getRawResult(): Unit = ()
  def setRawResult(x: Unit) {}

  def updatePendingTasks(): Unit =
  {
    var scope = ScopeSummary.empty
    val runnable = mutable.Set[Cell.Position]()

    val (cellsAndModules, inputs, outputs) = 
      CatalogDB.withDBReadOnly { implicit s => 
        (
          workflow.cellsAndModulesInOrder,
          InputArtifactRef.inputArtifactIdsForWorkflow(workflowId = workflow.id),
          OutputArtifactRef.outputArtifactRefsForWorkflow(workflowId = workflow.id),
        )
      }

    for((cell, module) <- cellsAndModules){
      logger.debug(s"Updating execution state for $cell [${module.packageId}.${module.commandId}]; Scope:$scope")
      var updatedState = 
        cell.state
      val predictedProvenance = 
        module.command.map { _.predictProvenance(module.arguments, module.properties) }
                      .getOrElse { ProvenancePrediction.default }

      def transitionToState(newState: ExecutionState.T)
      {
        updatedState = newState
        val (updated, timestamps) = CatalogDB.withDB { implicit s => 
          val updated = cell.updateState(newState)
          (updated, updated.timestamps)
        }
        DeltaBus.notifyStateChange(workflow, cell.position, newState, timestamps)
      }

      cell.state match { 
        case ExecutionState.DONE | ExecutionState.FROZEN | ExecutionState.RUNNING => 
        {
          // TODO: If we want to support speculative execution, we'll need to re-check 
          // DONE and RUNNING cells here.  In particular, we'll need to keep track of 
          // the scope of a RUNNING cell (and its predicted provenance) to kill and
          // re-launch it if necessary
          logger.trace(s"Already ${cell.state}")
        }
        case ExecutionState.ERROR | ExecutionState.CANCELLED => 
        {
          // Workflow aborts need to abort both the scheduler and the workflow cell state, and
          // generally it makes more sense to update the cells first.  As a result, it is possible
          // that we might hit an ERROR or CANCELLED cell (which generally means that the workflow
          // was literally just aborted).  

          logger.trace(s"Already ${cell.state}")

          // Once we hit an ERROR or CANCELLED cell, the current scope is invalid and we can learn
          // nothing more about the remaining cells.
          return
        }
        case ExecutionState.WAITING | ExecutionState.STALE => 
        {
          // WAITING cells can transition to 
          //   DONE -> if all of the cell's inputs are either VersionUnchanged or 
          //           ExactVersion with the same artifactId.
          //   RUNNING -> if all of the cell's inputs are ExactVersion with at least
          //              one having a different artifactId
          //   STALE -> if any of the cell's inputs are VersionChanged ArtifactDeleted
          //            or ExactVersion with a different artifactId.  
          //   WAITING -> otherwise... i.e., if not STALE and at least one input has
          //              a VersionUnknown

          // STALE cells are as above, but transitioning to STALE instead in the WAITING
          // case

          // To be more precise, we need to answer two questions
          //   1. Will we need to run the cell?  
          //        Y: if there is no prior result
          //        Y: if the cell is in a state with invalid input provenance
          //        Y: if any of the inputs are VersionChanged or ExactVersion with a
          //           different version in the new scope
          //        ?: if not Y and there is at least one input with UnknownVersion in
          //           the new scope
          //        N: if not Y and not ?
          //   2. If Y, then can we run the cell now?  [Y, N]
          //        Y: If all inputs are either (i) ExactVersion in the new scope, 
          //           (ii) VersionUnchanged and the input provenance is valid, or
          //           (iii) ArtifactWillBeDeleted (i.e., can we definitively come
          //           up with a scope for the cell).
          //        N: Otherwise
          //      or if N, then can we move to the DONE state?
          // (Maybe, _) -> State unchanged
          // (Y, Y) -> RUNNING
          // (Y, N) -> STALE
          // (N, Y) -> DONE
          // (N, N) -> State Unchanged

          // Question: Is there a situation where it's possible to have a (Maybe, Y)?
          // If not, we can just merge the (Maybe, _) and (N, N) cases.
          // 
          // Answer: No.  1.Maybe requires at least one UnknownVersion, and 2.Y does
          // not allow any UnknownVersion

          val inputProvenance: Option[Map[String, Identifier]] = 
            if(cell.resultId.isEmpty){
              logger.trace("No prior result.  Input provenance invalid.")
              None
            } else if(ExecutionState.PROVENANCE_NOT_VALID_STATES(cell.state)) {
              // As of right now, this should never happen.  WAITING and STALE both
              // have valid provenance... but let's be safe and follow the state
              // machine
              logger.trace("In a state without valid provenance.")
              None
            } else {
              logger.trace("Prior result is valid.")
              Some(inputs.getOrElse(cell.position, Map.empty))
            }

          // This is the answer to question 1 above.  Note that we're combining the
          // Maybe and No cases, since their effects are indistinguishable.  See
          // above for the discussion.
          val cellNeedsReexecution = 
            inputProvenance match {
              case None => true
              case Some(inputReads) => 
              {
                inputReads.exists { case (input, readVersion) => 
                  scope(input).priorVersionRequiresReexecution(readVersion)
                }
              }
            }

          // Now we look at the second question.
          val cellIsRunnable =
            if(predictedProvenance.openWorldReads){
              // If we can't predict what artifacts the cell is going to read, then
              // check to see if we have a valid prior execution before falling 
              // through to checking for a completely finalized scope.
              if(!cellNeedsReexecution && inputProvenance.isDefined){
                scope.isRunnableForKnownInputs(inputProvenance.get.keys)
              } else {
                scope.isRunnableForUnknownInputs 
              }
            } else {
              // We can properly predict the provenance!  Yay!  We only need to check
              // the subset of the inputs that we know are going to be read by the
              // cell.
              scope.isRunnableForKnownInputs(predictedProvenance.reads)
            }

          (cellNeedsReexecution, cellIsRunnable) match {
            case (true, true)   => 
            {
              logger.trace("Cell needs re-execution and is runnable.  Marking for execution.")
              scheduleCell(cell, module, scope)
              transitionToState(ExecutionState.RUNNING)
            }
            case (true, false)  => 
            {
              cell.state match {
                case ExecutionState.WAITING => 
                  logger.trace("Cell needs re-execution but is not runnable.  Marking as STALE.")
                  transitionToState(ExecutionState.STALE)
                case ExecutionState.STALE => 
                  logger.trace("Cell needs re-execution but is not runnable.  Already marked STALE.")
                // other cases excluded above
              }
            }
            case (false, true)  => 
            {
              logger.trace("Cell does not need re-execution and is runnable.  Marking as DONE.")
              transitionToState(ExecutionState.DONE)
            }
            case (false, false) => 
            {
              logger.trace("Cell does not need re-execution, but is not runnable.  Leaving unchanged.")
              if(cell.state.equals(ExecutionState.STALE)){
                logger.warn(s"Potential scheduler bug: Previously decided that $cell was stale, but no longer needs re-execution.")
                logger.warn(s"Please report this at https://github.com/VizierDB/vizier-scala/issues")
              }
            }
          }
        }
      }

      logger.trace("Moving to next cell")

      scope = scope.copyWithUpdatesFromCellMetadata(
        state = updatedState,
        outputs = outputs.getOrElse(cell.position, Seq.empty),
        predictedProvenance = predictedProvenance
      )
    }
  }

}
