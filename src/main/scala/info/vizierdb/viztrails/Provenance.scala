package info.vizierdb.viztrails

import scalikejdbc._

import info.vizierdb.types._
import info.vizierdb.catalog.{ Cell, OutputArtifactRef, InputArtifactRef, ArtifactRef }
import com.typesafe.scalalogging.LazyLogging

object Provenance
  extends LazyLogging
{
  def getScope(cell: Cell)(implicit session: DBSession): Map[String, Identifier] =
    getRefScope(cell).mapValues { _.artifactId.get }

  def getRefScope(cell: Cell)(implicit session: DBSession): Map[String, ArtifactRef] = 
  {
    val c = Cell.syntax
    val o = OutputArtifactRef.syntax
    withSQL {
      select
        .from(Cell as c)
        .join(OutputArtifactRef as o)
        .where.eq(c.resultId, o.resultId)
          .and.eq(c.workflowId, cell.workflowId)
          .and.lt(c.position, cell.position)
        .orderBy(c.position.desc)
    }.map { OutputArtifactRef(_) }
     .list.apply()
     // identifier = None means that the dataset was deleted
     .foldLeft(Map[String, ArtifactRef]()) { 
      (scope:Map[String, ArtifactRef], output:ArtifactRef) =>
        logger.trace(s"Get Scope: Adding $output")
        // Thanks to the orderBy above, the first version of each identifier
        // that we encounter should be the right one.
        if(scope contains output.userFacingName) { scope }
        else { scope ++ Map(output.userFacingName -> output) }
     }
     .filterNot { _._2.artifactId.isEmpty }
  }

  def updateScope(cell: Cell, scope: Map[String, Identifier])(implicit session: DBSession): Map[String, Identifier] = 
    updateScope(cell.outputs.map { o => o.userFacingName -> o.artifactId }, scope)
  def updateScope(outputs: Seq[(String, Option[Identifier])], scope: Map[String, Identifier]): Map[String, Identifier] =
  {
    val (deleteRefs, insertRefs) = outputs.partition { _._2.isEmpty }
    val deletions = deleteRefs.map { _._1 }.toSet
    val insertions = insertRefs.toMap.mapValues { _.get }
    scope.filterNot { case (k, v) => deletions(k) } ++ insertions
  }

  def updateRefScope(cell: Cell, scope: Map[String, ArtifactRef])(implicit session: DBSession): Map[String, ArtifactRef] = 
    updateRefScope(cell.outputs, scope)
  def updateRefScope(outputs: Seq[ArtifactRef], scope: Map[String, ArtifactRef]): Map[String, ArtifactRef] =
  {
    val (deleteRefs, insertRefs) = outputs.partition { _.artifactId.isEmpty }
    val deletions = deleteRefs.map { _.userFacingName }.toSet
    val insertions = insertRefs.map { x => x.userFacingName -> x }.toMap
    scope.filterNot { case (k, v) => deletions(k) } ++ insertions
  }

  def checkForConflicts(cell: Cell, scope: Map[String, Identifier])(implicit session: DBSession): Boolean =
    cell.inputs.iterator.exists { i => (scope contains i.userFacingName) &&
                                       (i.artifactId.map { scope(i.userFacingName) != _ }
                                                    .getOrElse { true }) }

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