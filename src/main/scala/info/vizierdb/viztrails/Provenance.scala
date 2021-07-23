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

sealed trait Provenance
{
  def needsARecomputeOnScope(scope: ScopeSummary): Boolean
}

case class ProvenanceForInvalidState(state: ExecutionState.T) extends Provenance
{
  def needsARecomputeOnScope(scope: ScopeSummary): Boolean = true
}

case class ProvenanceForStaleModule(moduleId: Identifier) extends Provenance
{
  def needsARecomputeOnScope(scope: ScopeSummary): Boolean = true
}

case class ValidProvenance(
  inputs: Map[String, Identifier],
  outputs: Map[String, Option[Identifier]]
) extends Provenance
  with LazyLogging
{
  def needsARecomputeOnScope(scope: ScopeSummary): Boolean =
  {
    for((userFacingName, artifactId) <- inputs.iterator){
      logger.trace(s"Checking input ${userFacingName} -> ${artifactId} for conflicts")
      if(scope contains userFacingName){
        logger.trace("Scope has input")
        if(artifactId.toLong == scope(userFacingName).get){
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

object Provenance
  extends LazyLogging
{

  def updateSuccessorState(cell: Cell, scopeAtStart: ScopeSummary)(implicit session: DBSession): Unit =
    updateCellStates(cell.successors, scopeAtStart)

  def updateCellStates(cells: Seq[Cell], scopeAtStart: ScopeSummary)(implicit session: DBSession): Unit =
  {
    // TODO: this update can be moved completely to the database as an UPDATE query
    //       ... but let's get it correct first.
    var scope: ScopeSummary = scopeAtStart

    for(curr <- cells){
      logger.debug(s"Updating execution state for $curr; $scope")
      curr.state match { 
        case ExecutionState.WAITING => {
          if(curr.provenance.needsARecomputeOnScope(scope)){ 
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
        scope = scope.withUpdates(curr)
      }
    }
  }
}

