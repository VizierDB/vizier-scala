package info.vizierdb.viztrails

import scala.collection.mutable
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.Workflow
import info.vizierdb.catalog.Cell
import scalikejdbc.DBSession
import info.vizierdb.catalog.Module

class RunningWorkflow(workflow: Workflow)
  extends Runnable
  with LazyLogging
{

  type Position = Int

  val pendingTasks = mutable.Map[Position, RunningCell]()

  def updateAllPendingTasks(implicit session: DBSession) = updatePendingTasks(workflow.cellsAndModulesInOrder)
  def updateAllTasksFollowing(cell: Cell)(implicit session: DBSession) = updatePendingTasks(cell.successorsWithModules)
  def updatePendingTasks(cells: Seq[(Cell, Module)])(implicit session: DBSession)
  {
    var scope = ScopePrediction.empty
    val runnable = mutable.Set[Position]()

    for((cell, module) <- cells){
      logger.debug(s"Updating execution state for $cell; $scope")
      cell.state match { 
        case ExecutionState.DONE => 
        {
          logger.debug("Already DONE")
        }
        case ExecutionState.WAITING | ExecutionState.STALE => 
        {
          ???
          // val provenance = curr.provenance
          // provenance.predictRecomputationNeededOnScope(scope) match {
          //   case None => 
          //     logger.debug("Can't predict re-execution need")
          // }

          // if(provenance.needsARecomputeOnPredictedScope(scope)){ 
          //   // There is a conflict.  The cell now officially needs to be re-executed.
          //   logger.debug(s"Conflict detected -> STALE")
          //   curr.updateState(ExecutionState.STALE)

          //   // Once we hit a STALE cell, the current scope is invalid and we can learn
          //   // nothing more about the remaining cells.
          //   return
          // } else {
          //   // There is no conflict, and we haven't hit the first stale cell yet.  
          //   // Can safely re-use the prior cell execution results
          //   logger.debug("No conflict -> DONE")
          //   curr.updateState(ExecutionState.DONE)
          // }
        }
      }
    }
  }


  def run
  {
    ???
  }

}