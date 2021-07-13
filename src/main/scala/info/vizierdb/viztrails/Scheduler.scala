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

import play.api.libs.json.{ JsValue, JsObject }
import scalikejdbc._
import java.util.concurrent.{ ForkJoinTask, ForkJoinPool }
import com.typesafe.scalalogging.LazyLogging

import info.vizierdb.types._
import info.vizierdb.commands._
import info.vizierdb.catalog.binders._
import info.vizierdb.catalog.{ Workflow, Cell, Result }
import info.vizierdb.delta.DeltaBus
import org.mimirdb.util.UnsupportedFeature

object Scheduler
  extends LazyLogging
{
  val workers = new ForkJoinPool(10)
  val runningWorkflows = scala.collection.mutable.Map[Identifier,WorkflowExecution]()

  /**
   * Schedule a workflow for execution.  This should be automatically called from the branch
   * mutator operations.  <b>Do not call this from within a DB Session</b>
   */
  def schedule(workflowId: Identifier) 
  {
    logger.debug(s"Scheduling Workflow ${workflowId}")
    this.synchronized {
      logger.trace(s"Acquired scheduler lock for ${workflowId}")
      if(runningWorkflows contains workflowId){
        logger.warn(s"Ignoring attempt to reschedule workflow ${workflowId}")
        return
      }
      logger.trace(s"Allocating execution manager for ${workflowId}")
      val executor = new WorkflowExecution(workflowId)
      runningWorkflows.put(workflowId, executor)
      logger.trace(s"Starting execution manager for ${workflowId}")
      workers.execute(executor)
      logger.trace(s"Done scheduling ${workflowId}")
    }
  }

  /**
   * Abort a (possibly) runnign workflow workflow.  This shouldn't be called directly.  Instead
   * use Workflow.abort or one of Branch's mutator operations.  <b>Do not call this from within 
   * a DB Session</b>
   */
  def abort(workflowId: Identifier)
  {
    logger.debug(s"Trying to abort Workflow ${workflowId}")
    this.synchronized {
      val executor = runningWorkflows.get(workflowId).getOrElse { return }
      logger.debug(s"Aborting Workflow ${workflowId}")
      if(!executor.isDone){ executor.cancel(true) }
      runningWorkflows.remove(workflowId)
    }
  }

  /**
   * Initialize the scheduler after a from-scratch startup, starting execution for all non-aborted
   * workflows with operations still pending.
   */
  def init(implicit session: DBSession)
  {
    logger.debug("Initalizing Viztrails scheduler")
    this.synchronized {
      sql"""
        SELECT workflow.id
        FROM workflow,
             (SELECT DISTINCT workflowid 
              FROM cell WHERE state = ${ExecutionState.STALE.id}) stale_cells
        WHERE workflow.id = stale_cells.workflowid
        """
    } .map { _.int(1) }.list.apply()
      .foreach { schedule(_) }
  }

  /**
   * A list of currently running workflows
   */
  def running(implicit session: DBSession): Seq[Workflow] =
  {
    logger.debug("Getting running workflows")
    this.synchronized {
      runningWorkflows
        .filterNot { _._2.isDone() }
        .map { _._1 }
        .toSeq
    }.map { 
      Workflow.get(_)
    }
  }


  /**
   * Free resources associated with the specified workflow if they are no longer needed.
   */
  def cleanup(workflowId: Identifier)
  {
    logger.debug("Cleaning up workflows")
    this.synchronized {
      val executor = runningWorkflows.get(workflowId).getOrElse { return }
      if(executor.isDone()) { runningWorkflows.remove(workflowId) }
    }
  }

  /**
   * Check to see if the specified workflow is still pending.  Aliased as Workflow.isPending
   */
  def isWorkflowPending(workflowId: Identifier): Boolean = 
  { 
    logger.debug("Checking for pending workflows")
    cleanup(workflowId); 
    this.synchronized { runningWorkflows contains workflowId } 
  }

  /**
   * Block until the specified workflow completes.  The workflow must already be scheduled.
   *
   * In general, this method should only be used for testing.  
   */
  def joinWorkflow(workflowId: Identifier, failIfNotRunning: Boolean = true)
  {
    logger.debug(s"Trying to join with Workflow ${workflowId}")
    val executorMaybe:Option[WorkflowExecution] = this.synchronized { 
      runningWorkflows.get(workflowId)
    }

    executorMaybe match {
      case None => 
        if(failIfNotRunning){
          throw new RuntimeException(s"Workflow $workflowId is not running or has already been cleaned up")
        }
      case Some(executor) =>
        executor.join() 
        cleanup(workflowId)
      }
  }
  /**
   * Register an error result for the provided cell
   *
   * @argument  cell      The cell to register an error for
   * @argument  message   The error messages
   * @return              The Result object for the cell
   */
  private def errorResult(cell: Cell)(implicit session: DBSession): Result = 
  {
    val result = cell.finish(ExecutionState.ERROR)._2
    val position = cell.position
    val workflow = cell.workflow

    val stateTransitions = 
      StateTransition.forAll( 
        sqls"position > ${cell.position}", 
        ExecutionState.PENDING_STATES -> ExecutionState.CANCELLED 
      )
    DeltaBus.notifyStateChange(workflow, cell.position, ExecutionState.ERROR)
    for(cell <- workflow.cellsWhere(sqls"""position > ${cell.position}""")) {
      DeltaBus.notifyStateChange(workflow, cell.position, ExecutionState.CANCELLED)
    }
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(
          c.state -> StateTransition.updateState(stateTransitions),
          c.resultId -> StateTransition.updateResult(stateTransitions)
        )
        .where.eq(c.workflowId, workflow.id)
          .and.gt(c.position, cell.position)
    }.update.apply()
    return result
  }
  /**
   * Register a execution result for the provided cell based on the provided execution context.
   * this may still trigger an error if one was registered during execution of the cell.
   *
   * @argument  cell      The cell to register an error for
   * @argument  message   The error message
   */
  private def normalResult(cell: Cell, context: ExecutionContext)(implicit session: DBSession): Result = 
  {
    if(context.isError){ return errorResult(cell) }
    val result = cell.finish(ExecutionState.DONE)._2
    val workflow = cell.workflow

    for((userFacingName, identifier) <- context.inputs) {
      result.addInput( userFacingName, identifier )
    }
    for((userFacingName, artifact) <- context.outputs) {
      result.addOutput( userFacingName, artifact.map { _.id } )
      artifact match {
        case None => 
          DeltaBus.notifyDeleteArtifact(
            workflow = workflow,
            position = cell.position,
            name = userFacingName
          )
        case Some(actualArtifact) => 
          DeltaBus.notifyOutputArtifact(
            workflow = workflow, 
            position = cell.position, 
            name = userFacingName, 
            artifactId   = actualArtifact.id, 
            artifactType = actualArtifact.t
          )
      }
    }
    DeltaBus.notifyStateChange(cell.workflow, cell.position, ExecutionState.DONE)

    Provenance.updateSuccessorState(cell, 
      Provenance.updateScope(
        context.outputs.mapValues { _.map { _.id } }.toSeq,
        context.scope.mapValues { _.id }
      )
    )
    for(cell <- workflow.cellsWhere(sqls"position > ${cell.position}")){
      DeltaBus.notifyStateChange(
        workflow = workflow,
        position = cell.position,
        newState = cell.state
      )
    }
    return result
  }

  /**
   * Evaluate a single cell synchronously.
   *
   * @argument   cell     The cell to process
   * @return              The Result object for the evaluated cell
   */
  def processSynchronously(cell: Cell): Result =
  {
    logger.trace(s"Processing $cell")
    val (command, arguments, context, startedCell) =
      DB autoCommit { implicit session =>
        val module = cell.module
        val (startedCell, result) = cell.start
        val refs = Provenance.getRefScope(startedCell)
        val scope = Provenance.refScopeToSummaryScope(refs)
        val workflow = startedCell.workflow
        val context = new ExecutionContext(
                            startedCell.projectId, 
                            scope, 
                            startedCell.workflow, 
                            startedCell, 
                            module, 
                            { (mimeType, data) => 
                                DB.autoCommit { implicit s =>
                                  result.addMessage( 
                                    mimeType = mimeType, 
                                    data = data 
                                  )(s)
                                  DeltaBus.notifyMessage(
                                    workflow = workflow, 
                                    position = cell.position, 
                                    mimeType = mimeType, 
                                    data = data,
                                    stream = StreamType.STDOUT
                                  )(s)
                                }
                            },
                            { message =>
                                DB.autoCommit { implicit s =>
                                  result.addMessage( 
                                    message = message,
                                    stream = StreamType.STDERR
                                  )(s)
                                  DeltaBus.notifyMessage(
                                    workflow = workflow, 
                                    position = cell.position, 
                                    mimeType = "text/plain", 
                                    data = message.getBytes,
                                    stream = StreamType.STDERR
                                  )(s)
                                }
                            }
                          )
        val command = 
          Commands.getOption(module.packageId, module.commandId)
                  .getOrElse { 
                    context.error(s"Command ${module.packageId}.${module.commandId} does not exist")
                    return errorResult(startedCell)
                  }
        val arguments = Arguments(module.arguments.as[Map[String, JsValue]], command.parameters)
        val argumentErrors = arguments.validate
        if(!argumentErrors.isEmpty){
          val msg = "Error in module arguments:\n"+argumentErrors.mkString("\n")
          logger.warn(msg)
          context.error(msg)
          return errorResult(startedCell)
        }
        DeltaBus.notifyStateChange(workflow, startedCell.position, startedCell.state)
        DeltaBus.notifyAdvanceResultId(workflow, startedCell.position, startedCell.resultId.get)
        /* return */ (command, arguments, context, startedCell)
      }
    logger.trace(s"About to Process [${command.name}]($arguments) <- ($context)")

    try {
      command.process(arguments, context)
    } catch {
      case e:Exception => {
        e.printStackTrace()
        context.error(s"An internal error occurred: ${e.getMessage()}")
        return DB autoCommit { implicit session => 
          errorResult(startedCell)
        }
      }
    }
    return DB autoCommit { implicit session => 
      normalResult(startedCell, context)
    }
  }

  class WorkflowExecution(workflowId: Identifier)
    extends ForkJoinTask[Unit]
  {
    var currentCellExecution = null

    // Workflow caches dependent cells, so this should be able to avoid going to the DB entirely
    def nextTarget: Option[Cell] = 
      DB readOnly { implicit session =>
        val c = Cell.syntax
        withSQL {
          select
            .from(Cell as c)
            .where.eq(c.state, ExecutionState.STALE)
              .and.eq(c.workflowId, workflowId)
            .orderBy(c.position)
            .limit(1)
        }.map { Cell(_) }.single.apply()
      }

    def aborted: Boolean =
      DB readOnly { implicit session =>
        val w = Workflow.syntax
        withSQL { 
          select(w.aborted)
            .from(Workflow as w)
            .where.eq(w.id, workflowId)
        }.map { _.int(1) > 0 }.single.apply().getOrElse { true }
      }
      // catalogTransaction {
      //   workflow.cells.filter { cell => /* println(cell); */ cell.state == ExecutionState.STALE }
      //                 .toIterator
      //                 .foldLeft(None:Option[Cell]) { 
      //                   case (None, y) => Some(y)
      //                   case (Some(x), y) if y.position < x.position => Some(y)
      //                   case (Some(x), _) => Some(x)
      //                 }
      // }

    def exec(): Boolean = 
    {
      try { 
        logger.debug(s"In executor for Workflow ${workflowId}")
        if(aborted) {
          logger.debug(s"Aborted processing of Workflow ${workflowId} before start")
        }
        logger.debug("Recomputing Cell States")
        DB autoCommit { implicit session => 
          Provenance.updateCellStates(Workflow.get(workflowId).cells, Map.empty)
        }
        logger.debug(s"Starting processing of Workflow ${workflowId}")
        var cell: Option[Cell] = nextTarget
        logger.debug(s"First target for Workflow ${workflowId}: $cell")
        while( (!aborted) &&  (cell != None) ){
          processSynchronously(cell.get)
          cell = nextTarget
        }
      } catch {
        case e: Exception => 
          logger.error(s"Error processing: $e")
          e.printStackTrace()
      }
      logger.debug(s"Done processing Workflow ${workflowId}")
      return true
    }
    def setRawResult(x: Unit): Unit = {}
    def getRawResult(): Unit = {}
  }
}

