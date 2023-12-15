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
import info.vizierdb.catalog.Artifact

object Provenance
  extends LazyLogging
{
  def getScope(cell: Cell)(implicit session: DBSession): Map[String, Identifier] =
    getRefScope(cell).mapValues { _.artifactId.get }

  def getArtifactScope(cell: Cell)(implicit session: DBSession): Map[String, Artifact] =
  {
    val refs = getRefScope(cell)
    refScopeToArtifactScope(refs)
  }
  def refScopeToArtifactScope(refs: Map[String, ArtifactRef])(implicit session: DBSession): Map[String, Artifact] =
  {
    val summaries = Artifact.getAll(refs.values.map { _.artifactId.get }.toSeq)
                            .map { a => a.id -> a }
                            .toMap
    refs.mapValues { ref => summaries(ref.artifactId.get) }
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
   * @return              false if the cell's prior execution can be applied directly without re-running the cell
   */
  def cellNeedsANewResult(cell: Cell, scope: Map[String, Identifier])(implicit session: DBSession): Boolean =
  {
    if(ExecutionState.PROVENANCE_NOT_VALID_STATES(cell.state)){
      logger.trace(s"Cell is in a provenance-not-valid state (${cell.state}).  Forcing a conflict")
      return true
    } else if(cell.resultId.isEmpty){
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

    for(curr <- cells){
      logger.debug(s"Updating execution state for $curr; $scope")
      curr.state match { 
        case ExecutionState.WAITING => {
          if(cellNeedsANewResult(curr, scope)){ 
            // There is a conflict.  The cell now officially needs to be re-executed.
            logger.debug(s"Conflict detected -> STALE")
            curr.updateState(ExecutionState.STALE)

            // Once we hit a STALE cell, the current scope is invalid and we can learn
            // nothing more about the remaining cells.
            return
          } else {
            // There is no conflict, and we haven't hit the first stale cell yet.  
            // Can safely re-use the prior cell execution results
            logger.debug("No conflict -> DONE")
            curr.updateState(ExecutionState.DONE)
          }
        }
        case ExecutionState.ERROR | ExecutionState.CANCELLED => {
          // Workflow aborts need to abort both the scheduler and the workflow cell state, and
          // generally it makes more sense to update the cells first.  As a result, it is possible
          // that we might hit an ERROR or CANCELLED cell (which generally means that the workflow
          // was literally just aborted).  

          logger.debug("Already ERROR or CANCELLED")

          // Once we hit an ERROR or CANCELLED cell, the current scope is invalid and we can learn
          // nothing more about the remaining cells.
          return
        }
        case ExecutionState.STALE => {
          logger.debug("Already STALE")
          // Once we hit a STALE cell, the current scope is invalid and we can learn
          // nothing more about the remaining cells.
          return
        }
        case ExecutionState.RUNNING => {
          // It's possible we'll hit a RUNNING cell if we're appending a
          // cell to the workflow.
          logger.debug("Already RUNNING")
          // Once we hit a STALE cell, the current scope is invalid and we can learn
          // nothing more about the remaining cells.
          return
        }
        case ExecutionState.DONE => {
          logger.debug("Already DONE")
        }
        case ExecutionState.FROZEN => {
          // Frozen cells are simply ignored
          logger.debug("Already FROZEN")
        }
      }
      if(curr.state != ExecutionState.FROZEN){
        scope = updateScope(curr, scope)
      }
    }
  }
}

