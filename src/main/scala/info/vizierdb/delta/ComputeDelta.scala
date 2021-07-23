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
package info.vizierdb.delta

import scalikejdbc._
import scala.collection.mutable.Buffer
import info.vizierdb.catalog._
import info.vizierdb.types._
import info.vizierdb.catalog.serialized.ModuleDescription
import info.vizierdb.viztrails.Provenance
import info.vizierdb.viztrails.ScopeSummary

object ComputeDelta
{

  /**
   * Compute a sequence of deltas to bring a system at the specified workflow state
   * up to the current head of the same branch.
   *
   * @param   start           The [WorkflowState] to compute deltas for.
   * @return                  The series of [WorkflowDelta]s needed to bring the
   *                          start state up to the branch head.
   */
  def apply(start: WorkflowState): Seq[WorkflowDelta] =
    DB.readOnly { implicit s => 
      apply(start, getState(start.branchId), Branch.get(start.branchId).head)
    }

  /**
   * Compute a sequence of deltas to bring a system at the specified start state
   * to the specified end state.
   *
   * @param  start            The *current* [WorkflowState].
   * @param  end              The desired end-state.  
   * @param  workflow         The workflow corresponding to `end`
   * @return                  A series of [WorkflowDelta]s that will bring the start
   *                          state up to the specified end state.
   *
   * This function assumes that both the start and end states belong to the same
   * branch.  Technically nothing should go wrong if this assumption doesn't hold,
   * but you'll end up just getting a series of deltas that inserts every cell in 
   * the new workflow and deleting every cell in the old one.
   */
  def apply(
    start: WorkflowState, 
    end: WorkflowState, 
    workflow: Workflow
  )(implicit session: DBSession): Seq[WorkflowDelta] =
  {
    val startModules = 
      start.cells
           .map { _.moduleId.toLong  }
           .zipWithIndex
           .toMap
    val endModules = 
      end.cells
         .map { m => 
           val endModuleId = traceModule(m.moduleId.toLong, startModules.keySet) 
           val endModuleIndex = endModuleId.map { startModules(_) }
           (m, endModuleIndex)
         }

    merge(
      start.cells.zipWithIndex,
      endModules,
      workflow
    )
  }

  /**
   * Render the deltas needed to go from from start to end
   * @param   start        The start [WorkflowDescription].cells.zipWithIndex
   * @param   end          The end [WorkflowDescription]'s .cells, paired with 
   *                       the index of the corresponding start cell (if one
   *                       exists)
   * @param   workflow     The [Workflow] corresponding to `end`
   * @return               The sequence of deltas requried to transform start to
   *                       end
   *
   * Both start and end should be unsorted (i.e., in the same order in which 
   * they appear in the [WorkflowDescription]).  
   * 
   * Deltas are rendered using a rough analog of an outer sort-merge join.  
   * The easiest way to convey this is through a tail-recursive description:
   * ---- Base Cases ----
   *  - Case 1: end.isEmpty -> Every remaining element of start is a cell that
   *                           got deleted.
   *  - Case 2: start.isEmpty -> Every remaining element of end is a cell that
   *                           got inserted.
   * ---- Recursive Cases ----
   *  - Case 1: end.head does not correspond to a cell in start -> It needs
   *                           to be inserted
   *  - Case 1.b: end.head is out of sorted order w.r.t. the corresponding start
   *                           cell's position -> Treat it as an insert+delete
   *  - Case 2: end.head corresponds to start.head -> Check if the cell got 
   *                           updated, but otherwise, no insert/delete needs to
   *                           happen.
   *  - Case 3: end.head corresponds to a cell that comes *after* start.head ->
   *                           start.head got deleted.
   *  - It should never be the case that end.head comes *before* the next 
   *    start.head, since start.head should have every integer represented in 
   *    order.
   */
  def merge(
    start: Seq[(CellState, Int)], 
    end: Seq[(CellState, Option[Int])],
    workflow: Workflow
  )(implicit session: DBSession): Seq[WorkflowDelta] =
  {
    var lhs = start
    var rhs = end
    var lastRhsIdx = -1
    var buffer = Buffer[WorkflowDelta]()
    var workflowSize = 0

    def describe(position: Int): ModuleDescription = 
    {
      val cell = workflow.cellByPosition(position).get
      cell.module.describe(
        cell,
        workflow.projectId,
        workflow.branchId,
        workflow.id,
        ScopeSummary(cell)
      )
    }

    for(i <- 0 until lhs.size + rhs.size + 1){
      /////////////// Base Case 1 ///////////////
      // If there are no more modules on the LHS, then insert all the remaining 
      // modules on the RHS.
      if(lhs.size == 0){
        for( (_, _) <- rhs){
          buffer.append(InsertCell(describe(workflowSize), workflowSize))
          workflowSize += 1
        }
        return buffer.toSeq
      } else
      /////////////// Base Case 2 ///////////////
      // If there are no more modules on the RHS, then delete all the remaining
      // modules on the LHS
      if(rhs.size == 0){
        for( _ <- lhs) {
          buffer.append(DeleteCell(workflowSize))
        }
        return buffer.toSeq
      } else
      /////////////// Recursive Case 1 ///////////////
      // If the RHS head is entirely new, or got moved from earlier in the list, 
      // then we should insert it here.
      if(rhs.head._2.isEmpty || rhs.head._2.get < lastRhsIdx){
        buffer.append(InsertCell(describe(workflowSize), workflowSize))
        // no need to update the lastRhsIdx here.
        rhs = rhs.tail
        workflowSize += 1
      } else
      /////////////// Recursive Case 2 ///////////////
      // If the LHS and RHS are the same module, then check to see if there are
      // any minor deltas to apply.
      if(lhs.head._2 == rhs.head._2.get){
        // Do this coarsely for now.  If there's *any* change to the cell state, 
        // send over a fresh copy.
        if(! lhs.head._1.equals(rhs.head._1) ){
          buffer.append(UpdateCell(describe(workflowSize), workflowSize))
        }
        lhs = lhs.tail
        lastRhsIdx = rhs.head._2.get
        rhs = rhs.tail
        workflowSize += 1
      } else
      /////////////// Recursive Case 3 ///////////////
      // If the next LHS module occurs sequentially before the next RHS module, 
      // then delete it (if it got moved later in the list, then delete here)
      // and insert later.
      if(lhs.head._2 < rhs.head._2.get){
        buffer.append(DeleteCell(workflowSize))
      } else 
      /////////////// Recursive Case 4 ///////////////
      // If the next RHS module occurs sequentially before the next LHS module,
      // then this is a problem (since all of the lhs modules should be sorted)
      {
        assert(false, "Should not be here")
      }

    }
    throw new RuntimeException("Merge entered into an infinite loop")
  }

  /**
   * Search the provenance of a particular module for one of several specific modules.
   * 
   * @param  target          The identifier of the module who's provenance we're searching
   * @param  searchFor       A set of module identifiers to search for.
   * @return                 The first element of searchFor encountered in the provenance or
   *                         None if none of them appear.
   */
  def traceModule[T](target: Identifier, searchFor: Set[Identifier])
                    (implicit session: DBSession): Option[Identifier] =
  {
    var current = target
    while(!searchFor(current)){
      Module.get(target).revisionOfId match {
        case None         => return None
        case Some(module) => current = module
      }
    }
    return Some(current)
  }

  /**
   * Compute the WorkflowState object for the head of the specified branch
   */
  def getState(branchId: Identifier)(implicit session: DBSession): WorkflowState = 
    getState(Branch.get(branchId))
  
  /**
   * Compute the WorkflowState object for the head of the specified branch
   */
  def getState(branch: Branch)(implicit session: DBSession): WorkflowState = 
  {
    val head = branch.head
    val cellsAndModules = head.cellsAndModulesInOrder
    WorkflowState(
      branchId = branch.id,
      workflowId = head.id,
      cells = cellsAndModules.map { case (cell, module) =>
        CellState(
          moduleId = module.id.toString,
          resultId = cell.resultId.map { _.toString },
          state = cell.state,
          messageCount = cell.messages.size
        )
      }
    )
  }
}