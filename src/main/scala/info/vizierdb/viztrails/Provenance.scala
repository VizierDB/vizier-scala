package info.vizierdb.viztrails

import org.squeryl.PrimitiveTypeMode._

import info.vizierdb.Types._
import com.typesafe.scalalogging.LazyLogging

object Provenance
  extends LazyLogging
{
  def getScope(cell: Cell): Map[String, Identifier] = 
  {
    from(Viztrails.cells, Viztrails.outputs){ case (c, o) => 
      where( (c.resultId === o.resultId) 
         and (c.workflowId === cell.workflowId)
       ).select( o.userFacingName, o.artifactId )
        .orderBy(c.position.desc)
    }.foldLeft(Map[String, Identifier]()) { 
      (scope:Map[String, Identifier], output:(String, Identifier)) =>
        logger.trace("Get Scope: Adding $output")
        // Thanks to the orderBy above, the first version of each identifier
        // that we encounter should be the right one.
        if(scope contains output._1) { scope }
        else { scope ++ Map(output) }
    }
  }

  def updateScope(cell: Cell, scope: Map[String, Identifier]): Map[String, Identifier] = 
    scope ++ cell.outputs.map { o => o.userFacingName -> o.artifactId }.toMap

  def checkForConflicts(cell: Cell, scope: Map[String, Identifier]): Boolean =
    cell.inputs.iterator.exists { i => (scope contains i.userFacingName) &&
                                       (scope(i.userFacingName) != i.artifactId) }

  def updateSuccessorState(cell: Cell, outputs: Map[String, Identifier]): Unit =
  {
    // TODO: this check completely to the database
    var scope = outputs
    var hitFirstStaleCell = false
    val successors = cell.successors

    Viztrails.cells.update(
      cell.successors
          .map { curr => 
            curr.state match {
              case ExecutionState.STALE => {
                hitFirstStaleCell = true
              }
              case ExecutionState.WAITING => {
                if(checkForConflicts(curr, scope)){ 
                  // There is a conflict.  The cell now officially needs to be re-executed.
                  hitFirstStaleCell = true
                  cell.state = ExecutionState.STALE
                } else if(!hitFirstStaleCell) {
                  // There is no conflict, and we haven't hit the first stale cell yet.  
                  // Can safely re-use the prior cell execution results
                  cell.state = ExecutionState.DONE
                }
              }

              case ExecutionState.ERROR | ExecutionState.DONE => {
                throw new RuntimeException("Invalid state.  DONE or ERROR states should never follow a STALE cell")
              }
            }
            scope = updateScope(curr, scope)
            /* return[map] */ curr
          }
    )
  }
}