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
package info.vizierdb.catalog

import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax
import java.time.ZonedDateTime
import play.api.libs.json._
import info.vizierdb.types._
import java.time.format.DateTimeFormatter
import info.vizierdb.util.HATEOAS
import info.vizierdb.catalog.binders._
import info.vizierdb.VizierAPI
import info.vizierdb.util.StupidReactJsonMap
import info.vizierdb.viztrails.{ Scheduler, StateTransition, ScopeSummary }
import info.vizierdb.delta.DeltaBus
import ExecutionState.{ WAITING, STALE, RUNNING, ERROR, CANCELLED, DONE, FROZEN }

/**
 * One branch of the project
 */
case class Branch(
  id: Identifier,
  projectId: Identifier,
  name: String,
  properties: JsObject,
  headId: Identifier,
  created: ZonedDateTime,
  modified: ZonedDateTime,
  createdFromBranchId: Option[Identifier],
  createdFromWorkflowId: Option[Identifier]
)
{
  /**
   * Retrieve the head [[Workflow]]
   */
  def head(implicit session: DBSession): Workflow = Workflow.get(headId)

  /**
   * Update the branch head by creating a module and appending it to the workflow
   * 
   * @param packageId       The package id of the module's command
   * @param commandId       The command id of the module's command
   * @param args            The module's arguments
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def append(packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    append(Module.make(packageId, commandId)(args:_*))

  /**
   * Update the branch head by creating a module and inserting it into the workflow
   * 
   * @param position        The position of the newly inserted cell
   * @param packageId       The package id of the module's command
   * @param commandId       The command id of the module's command
   * @param args            The module's arguments
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def insert(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    insert(position, Module.make(packageId, commandId)(args:_*))

  /**
   * Update the branch head by creating a module and replacing an existing cell with it
   * 
   * @param position        The position of the cell to modify
   * @param packageId       The package id of the module's command
   * @param commandId       The command id of the module's command
   * @param args            The module's arguments
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def update(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    update(position, Module.make(packageId, commandId)(args:_*))

  /**
   * Update the branch head by appending a module to the workflow
   * 
   * @param module          The module to append
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def append(module: Module)(implicit session: DBSession): (Branch, Workflow) = 
  {
    val ret = modify(
      module = Some(module),
      action = ActionType.APPEND,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      addModules = Seq(module.id -> Workflow.getLength(headId)),
    )
    DeltaBus.notifyCellAppend(ret._2)
    return ret
  }

  /**
   * Update the branch head by inserting a module into the workflow
   * 
   * @param position        The position of the newly inserted cell
   * @param module          The module to insert
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def insert(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      updatePosition = sqls"case when position >= $position then position + 1 else position end",
      recomputeCellsFrom = position,
      addModules = Seq(module.id -> position)
    )
    DeltaBus.notifyCellInserts(ret._2){ (cell, _) => cell.position == position }
    for(cell <- ret._2.cellsWhere(sqls"position > ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Update the branch head by replacing a cell in the workflow with a new module
   * 
   * @param position        The position of the cell to replace
   * @param module          The module to replace the cell with
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def update(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) = 
  {
    val ret = modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      recomputeCellsFrom = position,
      keepCells = sqls"position <> $position",
      addModules = Seq(module.id -> position)
    )
    DeltaBus.notifyCellUpdates(ret._2) { (cell, _) => cell.position == position }
    for(cell <- ret._2.cellsWhere(sqls"position > ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Invalidate all cells in the workflow
   * 
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def invalidate(cells: Set[Int] = Set.empty)
                (implicit session: DBSession): (Branch, Workflow) = 
  {
    val stateUpdate = 
      if(cells.isEmpty){
        StateTransition( sqls"1=1", DONE -> STALE )
      } else {
        StateTransition( sqls.in(sqls"position", cells.toSeq), DONE -> STALE )
      }
    val ret =
      modify(
        module = None,
        action = ActionType.INSERT,
        prevWorkflowId = headId,
        abortPrevWorkflow = true,
        recomputeCellsFrom = 0,
        updateState = stateUpdate
      )
    for(cell <- ret._2.cells){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Update the branch head by deleting a cell from the workflow
   * 
   * @param position        The position of the cell to delete
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def delete(position: Int)(implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.DELETE,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      updatePosition = sqls"case when position > $position then position - 1 else position end",
      recomputeCellsFrom = position,
      keepCells = sqls"position <> $position"
    )
    DeltaBus.notifyCellDelete(ret._2, position)
    for(cell <- ret._2.cellsWhere(sqls"position >= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Update the branch head by freezing one cell in the workflow
   * 
   * @param position        The position of the cell to freeze
   * @return                The updated Branch object and the new head [[Workflow]]
   * 
   * A frozen cell is temporarily removed from the workflow.  Its result persists
   * and is visible, but the cell itself is never scheduled for reexecution, and 
   * its outputs are not visible to subsequent cells.
   */
  def freezeOne(position: Int)(implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.DELETE,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      updateState = 
        StateTransition.forAll(sqls"position = $position" , FROZEN),
      recomputeCellsFrom = position+1
    )
    for(cell <- ret._2.cellsWhere(sqls"position >= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Update the branch head by thawing one cell in the workflow
   * 
   * @param position        The position of the cell to thaw
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def thawOne(position: Int)(implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      updateState = 
        StateTransition( sqls"position = $position", FROZEN -> WAITING ),
      recomputeCellsFrom = position+1
    )
    for(cell <- ret._2.cellsWhere(sqls"position >= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Update the branch head by freezing cells in workflow starting from a position
   * 
   * @param position        The position of the first cell to freeze
   * @return                The updated Branch object and the new head [[Workflow]]
   * 
   * A frozen cell is temporarily removed from the workflow.  Its result persists
   * and is visible, but the cell itself is never scheduled for reexecution, and 
   * its outputs are not visible to subsequent cells.
   */
  def freezeFrom(position: Int)
                (implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.FREEZE,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      updateState = 
        StateTransition.forAll(sqls"position >= $position", FROZEN)
    )
    for(cell <- ret._2.cellsWhere(sqls"position >= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Update the branch head by thawing cells in the workflow prior to a position
   * 
   * @param position        The position of the last cell to thaw
   * @return                The updated Branch object and the new head [[Workflow]]
   */
  def thawUpto(position: Int)
              (implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.FREEZE,
      prevWorkflowId = headId,
      abortPrevWorkflow = true,
      updateState = 
        StateTransition(sqls"position <= $position", FROZEN -> WAITING),
      recomputeCellsFrom = position + 1
    )
    for(cell <- ret._2.cellsWhere(sqls"position <= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

  /**
   * Create a barebones, empty workflow
   */
  private[vizierdb] def initWorkflow(
    prevId: Option[Identifier] = None,
    action: ActionType.T = ActionType.CREATE,
    actionModuleId: Option[Identifier] = None,
    setHead: Boolean = true,
    createdAt: Option[ZonedDateTime] = None
  )(implicit session: DBSession): (Branch, Workflow) = {
    val w = Workflow.column
    val now = ZonedDateTime.now()
    val workflowId = withSQL {
      insertInto(Workflow)
        .namedValues(
          w.prevId -> prevId,
          w.branchId -> id,
          w.action -> action,
          w.actionModuleId -> actionModuleId,
          w.created -> createdAt.getOrElse { now },
          w.aborted -> false
        )
    }.updateAndReturnGeneratedKey.apply()
    val workflow = Workflow.get(workflowId)

    if(setHead) { 
      Branch.setHead(id, workflowId)
      return (Branch.get(id), workflow)
    } else {
      return (this, workflow)
    }
  }

  val DEFAULT_STATE_TRANSITIONS = 
    StateTransition(RUNNING -> STALE) ++
    StateTransition(ERROR -> STALE) ++
    StateTransition(CANCELLED -> WAITING)

  /**
   * Utility method to create a workflow derived from the current branch head
   * 
   * @param module             The module involved in the action
   * @param action             The action type
   * @param prevWorkflowId     The workflowId of the prior workflow (defaults to the branch head)
   * @param updatePosition     A [[SQLSyntax]] defining the position of cells in the new workflow 
   *                           (defaults to the identity function)
   * @param updateState        A [[Seq]] of [[StateTransitions]] describing how cell states should
   *                           be revised in the derived workflow.  By default, the state 
   *                           transitions in [[Branch.DEFAULT_STATE_TRANSITIONS]] will be used
   *                           with a final fallback of the identity function.  
   *                           [[StateTransition]]s provided here supersede this default behavior.
   *                           also see the recomputeCellsFrom parameter.
   * @param recomputeCellsFrom If provided, cells at or after the position specified in this 
   *                           parameter will be scheduled for potential re-exeution.
   * @param keepCells          A [[SQLSyntax]] with a boolean-valued expression.  Cells in the
   *                           <b>original</b> workflow for which this expression evaluates to
   *                           false will be dropped from the derived one.
   * @param addModules         A set of module identifiers to add as new cells, provided as 
   *                           <tt>(moduleId -> position)</tt> pairs.
   * @return                   The updated Branch object and the new head [[Workflow]]
   * 
   * Note that all [[SQLSyntax]] expressions (updatePosition, updateState, keepCells) are 
   * evaluated on the cells of the <b>original</b> workflow.  Positions are thus the positions 
   * from the original workflow.
   */
  private def modify(
    module: Option[Module],
    action: ActionType.T,
    prevWorkflowId: Identifier = headId,
    updatePosition: SQLSyntax = sqls"position",
    updateState: Seq[StateTransition] = Seq.empty,
    recomputeCellsFrom: Int = -1,
    keepCells: SQLSyntax = sqls"1=1",
    addModules: Iterable[(Identifier, Int)] = Seq(),
    abortPrevWorkflow: Boolean = false
  )(implicit session: DBSession): (Branch, Workflow) = 
  {
    // Note: we're working with immutable objects here.  `branch` will be the 
    // new instance with the updated headId
    val (branch, workflow) = initWorkflow(
      prevId = Some(prevWorkflowId),
      action = action,
      actionModuleId = module.map { _.id }
    )
    if(abortPrevWorkflow){
      Workflow.get(prevWorkflowId).abortIfNeeded
    }


    val stateTransitions = 
      updateState ++ (
        if(recomputeCellsFrom >= 0){
          StateTransition( sqls"position >= $recomputeCellsFrom", DONE -> WAITING)
        } else { Seq.empty }
      ) ++ DEFAULT_STATE_TRANSITIONS

    withSQL {
      val c = Cell.syntax
      insertInto(Cell)
        .select(
          sqls"""${workflow.id} as workflow_id""",
          updatePosition + sqls" as position",
          c.moduleId,
          StateTransition.updateResult(stateTransitions) + sqls" as resultId",
          StateTransition.updateState(stateTransitions) + sqls" as state",
          c.created,
        ) {
          _.from(Cell as c)
           .where.eq(c.workflowId, headId).and(Some(keepCells))
        }
    }.update.apply()
    for((moduleId, position) <- addModules){
      Cell.make(
        workflowId = workflow.id,
        position = position,
        moduleId = moduleId,
        resultId = None,
        state = ExecutionState.STALE,
        created = ZonedDateTime.now()
      )
    }

    return (branch, workflow)
  }

  /**
   * The id of the module used in the first branch operation that created this branch.
   * 
   * @return        The identifier of the Module or None if no module was used
   */
  def createdFromModuleId(implicit session:DBSession): Option[Identifier] =
    createdFromWorkflowId.flatMap { Workflow.get(_).actionModuleId }

  /**
   * Initialize this branch by cloning an existing workflow.
   * 
   * @return                   The updated Branch object and the new head [[Workflow]]
   */
  private[vizierdb] def cloneWorkflow(workflowId: Identifier)(implicit session: DBSession): (Branch, Workflow) =
    modify(None, ActionType.CREATE, workflowId)

  /**
   * Enumerate the [[Workflow]]s of this branch
   */
  def workflows(implicit session:DBSession): Seq[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.branchId, id)  
    }.map { Workflow(_) }.list.apply()

  /**
   * Retrieve the [[Workflow]] used to create this branch.
   */
  def createdFromWorkflow(implicit session:DBSession): Option[Workflow] = 
    createdFromWorkflowId.map { Workflow.get(_) }

  /**
   * Generate a Branch Description, suitable for sending to a Vizier frontend
   */
  def describe(implicit session:DBSession): JsObject =
    JsObject(
      summarize.value ++ Map(
        "workflows" -> Json.toJson(workflows.map { _.summarize })
      )
    )

  /**
   * Generate a Branch Summary, suitable for sending to a Vizier frontend 
   * 
   * A Workflow Branch is a simplified Branch Description (does not contain workflows)
   */
  def summarize(implicit session:DBSession): JsObject = 
    Json.obj(
      "id"             -> JsString(id.toString),
      "createdAt"      -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(created),
      "lastModifiedAt" -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(modified),
      "sourceBranch"   -> createdFromBranchId.map { _.toString },
      "sourceWorkflow" -> createdFromWorkflowId.map { _.toString },
      "sourceModule"   -> createdFromModuleId.map { _.toString },
      "isDefault"      -> Project.get(projectId).activeBranchId.equals(id),
      "properties"     -> StupidReactJsonMap(properties.value.toMap, "name" -> JsString(name)),
      HATEOAS.LINKS    -> HATEOAS(
        HATEOAS.SELF           -> VizierAPI.urls.getBranch(projectId, id),
        HATEOAS.BRANCH_DELETE  -> VizierAPI.urls.deleteBranch(projectId, id),
        HATEOAS.BRANCH_HEAD    -> VizierAPI.urls.getBranchHead(projectId, id),
        HATEOAS.BRANCH_UPDATE  -> VizierAPI.urls.updateBranch(projectId, id),
      ),
    )

  /**
   * Delete this branch and all contained workflows.
   */
  def deleteBranch(implicit session: DBSession)
  {
    for(workflow <- workflows){
      workflow.deleteWorkflow
    }
    withSQL {
      val b = Branch.syntax
      deleteFrom(Branch)
        .where.eq(b.id, id)
    }.update.apply()
  }

  /**
   * Update the properties field of this branch
   * 
   * @param name        The new name of this branch
   * @param properties  A dictionary of properties to associate with this branch
   */ 
  def updateProperties(
    name: String,
    properties: Map[String, JsValue]
  )(implicit session: DBSession): Branch =
  {
    val now = ZonedDateTime.now()
    withSQL {
      val b = Branch.column
      scalikejdbc.update(Branch)
        .set(b.name       -> Option(name).getOrElse { this.name },
             b.properties -> Option(properties)
                                      .map { JsObject(_) }
                                      .getOrElse { this.properties }
                                      .toString,
             b.modified   -> now)
        .where.eq(b.id, id)
    }.update.apply()
    Branch.get(id)
  }
}
object Branch 
  extends SQLSyntaxSupport[Branch]
{
  def apply(rs: WrappedResultSet): Branch = autoConstruct(rs, (Branch.syntax).resultName)
  override def columns = Schema.columns(table)

  /**
   * Retrieve the specified Branch
   */
  def get(target: Identifier)(implicit session:DBSession): Branch = getOption(target).get

  /**
   * Retrieve the specified Branch, returning None if the branch does not exist.
   */
  def getOption(target: Identifier)(implicit session:DBSession): Option[Branch] = 
    withSQL { 
      val b = Branch.syntax 
      select
        .from(Branch as b)
        .where.eq(b.id, target) 
    }.map { apply(_) }.single.apply()

  /**
   * Retrieve the specified Branch, erroring if the branch does not exist or if it is
   * not part of the specified [[Project]].
   */
  def get(projectId: Identifier, branchId: Identifier)(implicit session:DBSession): Branch = 
    getOption(projectId, branchId).get

  /**
   * Retrieve the specified Branch, returning None if the branch does not exist or if it is
   * not part of the specified [[Project]].
   */
  def getOption(projectId: Identifier, branchId: Identifier)(implicit session:DBSession): Option[Branch] = 
    withSQL { 
      val b = Branch.syntax 
      select
        .from(Branch as b)
        .where.eq(b.id, branchId) 
          .and.eq(b.projectId, projectId)
    }.map { apply(_) }.single.apply()

  def withName(projectId: Identifier, name: String)(implicit session:DBSession): Option[Branch] = 
    withSQL {
      val b = Branch.syntax 
      select
        .from(Branch as b)
        .where.eq(b.name, name)
          .and.eq(b.projectId, projectId)
    }.map { apply(_) }.list.apply().headOption

  /**
   * Overwrite the Branch Head (DO NOT USE)
   * 
   * Self-contained mechanism for manipulating the branch head.  In general, this method
   * should be avoided.  
   * 
   * USE CLASS METHODS ON [[Branch]] INSTEAD
   * 
   * This method is here mainly to facilitate the manual manipulation needed
   * for import/export.
   */
  private[vizierdb] def setHead(branchId: Identifier, workflowId: Identifier)(implicit session: DBSession) =
  {
    val now = ZonedDateTime.now()
    val b = Branch.column
    withSQL {
      scalikejdbc.update(Branch)
        .set(b.modified -> now, b.headId -> workflowId)
        .where.eq(b.id, branchId)
    }.update.apply()
  }
}

