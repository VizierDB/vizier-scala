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

import info.vizierdb.types._
import info.vizierdb.catalog.{ Cell, OutputArtifactRef, InputArtifactRef, ArtifactRef }
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.{ Artifact, ArtifactSummary }

object Provenance
  extends LazyLogging
{
  def getScope(cell: Cell)(implicit session: DBSession): Map[String, Identifier] =
    getRefScope(cell).mapValues { _.artifactId.get }

  def getSummaryScope(cell: Cell)(implicit session: DBSession): Map[String, ArtifactSummary] =
  {
    val refs = getRefScope(cell).mapValues { _.artifactId.get }
    val summaries = Artifact.lookupSummaries(refs.values.toSeq)
                            .map { a => a.id -> a }
                            .toMap
    refs.mapValues { summaries(_) }
  }

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

  /**
   * Check to see whether the provided cell needs re-execution if the following scope is provided
   * @param    cell       The cell to check
   * @param    scope      The artifact state prior to the cell's execution
   * @return              true if the cell's prior execution can be applied directly without re-running the cell
   */
  def checkForConflicts(cell: Cell, scope: Map[String, Identifier])(implicit session: DBSession): Boolean =
  {
    if(cell.resultId.isEmpty){
      logger.trace("Cell has no result.  By default this is a conflict.")
      return true
    } else {
      for(i <- cell.inputs.iterator){
        logger.trace(s"Checking input ${i.userFacingName} -> ${i.artifactId} for conflicts")
        if(i.artifactId.isEmpty){ 
          logger.trace("Strangely, the input does not have an artifactId; assuming a conflict")
          return true
        }
        if(scope contains i.userFacingName){
          logger.trace("Scope has input")
          if(i.artifactId.get.toLong == scope(i.userFacingName)){
            logger.trace("Input matches prior execution; Trying next")
          } else {
            logger.trace("Input is a conflict")
            return true
          }
        } else { 
          logger.trace("Input deleted from scope: Conflict")
          return true 
        }
      }
      return false
    }
  }

  def updateSuccessorState(cell: Cell, outputs: Map[String, Identifier])(implicit session: DBSession): Unit =
    updateCellStates(cell.successors, outputs)

  def updateCellStates(cells: Seq[Cell], outputsAtStart: Map[String, Identifier])(implicit session: DBSession): Unit =
  {
    // TODO: this update can be moved completely to the database as an UPDATE query
    //       ... but let's get it correct first.
    var scope = outputsAtStart
    var hitFirstStaleCell = false
    
    for(curr <- cells){
      logger.debug(s"Updating execution state for $curr; $scope")
      curr.state match {
        case ExecutionState.STALE => {
          logger.debug(s"Already STALE")
          hitFirstStaleCell = true
        }
        case ExecutionState.WAITING => {
          if(checkForConflicts(curr, scope)){ 
            // There is a conflict.  The cell now officially needs to be re-executed.
            logger.debug(s"Conflict detected -> STALE")
            hitFirstStaleCell = true
            curr.updateState(ExecutionState.STALE)
          } else if(!hitFirstStaleCell) {
            // There is no conflict, and we haven't hit the first stale cell yet.  
            // Can safely re-use the prior cell execution results
            logger.debug("No conflict -> DONE")
            curr.updateState(ExecutionState.DONE)
          } else {
            logger.debug("Already hit stale cell -> WAITING")
          }
        }
        case ExecutionState.DONE => {
          logger.debug("Already DONE")
        }
        case ExecutionState.FROZEN => {
          logger.debug("Already FROZEN")
        }
        case ExecutionState.ERROR | ExecutionState.CANCELLED => {
          if(hitFirstStaleCell){
            throw new RuntimeException(s"Invalid state.  ${curr.state} states should never follow a STALE cell")
          } else { 
            logger.debug(s"Already ${curr.state}; Skipping rest of workflow")
            return
          }
        }
      }    
      scope = updateScope(curr, scope)
    }
  }
}

