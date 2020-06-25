package info.vizierdb.viztrails

import scalikejdbc._

import info.vizierdb.types._
import info.vizierdb.catalog.{ Cell, OutputArtifactRef, InputArtifactRef }
import com.typesafe.scalalogging.LazyLogging

object Provenance
  extends LazyLogging
{
  def getScope(cell: Cell)(implicit session: DBSession): Map[String, Identifier] = 
  {
    val c = Cell.syntax
    val o = OutputArtifactRef.syntax
    withSQL {
      select(o.userFacingName, o.artifactId)
        .from(Cell as c)
        .join(OutputArtifactRef as o)
        .where.eq(c.resultId, o.resultId)
          .and.eq(c.workflowId, cell.workflowId)
          .and.lt(c.position, cell.position)
        .orderBy(c.position.desc)
    }.map { rs => rs.string(1) -> rs.long(2) }
     .list.apply()
     .foldLeft(Map[String, Identifier]()) { 
      (scope:Map[String, Identifier], output:(String, Identifier)) =>
        logger.trace("Get Scope: Adding $output")
        // Thanks to the orderBy above, the first version of each identifier
        // that we encounter should be the right one.
        if(scope contains output._1) { scope }
        else { scope ++ Map(output) }
    }
  }

  def updateScope(cell: Cell, scope: Map[String, Identifier])(implicit session: DBSession): Map[String, Identifier] = 
    scope ++ cell.outputs.map { o => o.userFacingName -> o.artifactId }.toMap

  def checkForConflicts(cell: Cell, scope: Map[String, Identifier])(implicit session: DBSession): Boolean =
    cell.inputs.iterator.exists { i => (scope contains i.userFacingName) &&
                                       (scope(i.userFacingName) != i.artifactId) }

  def updateSuccessorState(cell: Cell, outputs: Map[String, Identifier])(implicit session: DBSession): Unit =
  {
    // TODO: this update can be moved completely to the database as an UPDATE query
    //       ... but let's get it correct first.
    var scope = outputs
    var hitFirstStaleCell = false
    val successors = cell.successors
    
    for(curr <- cell.successors){
      logger.trace(s"Updating execution state for $curr")
      curr.state match {
        case ExecutionState.STALE => {
          hitFirstStaleCell = true
        }
        case ExecutionState.WAITING => {
          if(checkForConflicts(curr, scope)){ 
            // There is a conflict.  The cell now officially needs to be re-executed.
            hitFirstStaleCell = true
            curr.updateState(ExecutionState.STALE)
          } else if(!hitFirstStaleCell) {
            // There is no conflict, and we haven't hit the first stale cell yet.  
            // Can safely re-use the prior cell execution results
            curr.updateState(ExecutionState.DONE)
          }
        }
        case ExecutionState.ERROR | ExecutionState.DONE => {
          throw new RuntimeException("Invalid state.  DONE or ERROR states should never follow a STALE cell")
        }
        scope = updateScope(curr, scope)
      }    
    }
  }
}