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
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.VizierAPI
import info.vizierdb.util.StupidReactJsonMap
import info.vizierdb.viztrails.Provenance
import info.vizierdb.delta.DeltaBus

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
  def head(implicit session: DBSession): Workflow = Workflow.get(headId)
  def append(module: Module)(implicit session: DBSession): (Branch, Workflow) = 
  {
    val ret = modify(
      module = Some(module),
      action = ActionType.APPEND,
      prevWorkflowId = headId,
      addModules = Seq(module.id -> Workflow.getLength(headId))
    )
    DeltaBus.notifyCellAppend(ret._2)
    return ret
  }
  def append(packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    append(Module.make(packageId, commandId)(args:_*))
  def insert(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      updatePosition = sqls"case when position >= $position then position + 1 else position end",
      updateState = sqls"""
          case when state = ${ExecutionState.FROZEN.id} then ${ExecutionState.FROZEN.id} 
               when position >= $position then ${ExecutionState.WAITING.id} 
               else state end
      """,
      addModules = Seq(module.id -> position)
    )
    DeltaBus.notifyCellInserts(ret._2){ (cell, _) => cell.position == position }
    for(cell <- ret._2.cellsWhere(sqls"position > ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }
  def delete(position: Int)(implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.DELETE,
      prevWorkflowId = headId,
      updatePosition = sqls"case when position > $position then position - 1 else position end",
      updateState = sqls"""
          case when state = ${ExecutionState.FROZEN.id} then ${ExecutionState.FROZEN.id} 
               when position >= $position then ${ExecutionState.WAITING.id} 
               else state end
      """,
      keepCells = sqls"position <> $position",
      forceRecomputeState = true
    )
    DeltaBus.notifyCellDelete(ret._2, position)
    for(cell <- ret._2.cellsWhere(sqls"position >= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }
  def insert(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    insert(position, Module.make(packageId, commandId)(args:_*))
  def update(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) = 
  {
    val ret = modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      updateState = sqls"""
          case when state = ${ExecutionState.FROZEN.id} then ${ExecutionState.FROZEN.id}
               when position >= $position then ${ExecutionState.WAITING.id} 
               else state end
      """,
      keepCells = sqls"position <> $position",
      addModules = Seq(module.id -> position)
    )
    DeltaBus.notifyCellUpdates(ret._2) { (cell, _) => cell.position == position }
    for(cell <- ret._2.cellsWhere(sqls"position > ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }
  def update(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    update(position, Module.make(packageId, commandId)(args:_*))
  def freezeFrom(position: Int)
                (implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.FREEZE,
      prevWorkflowId = headId,
      updateState = sqls"""
          case when position >= $position then ${ExecutionState.FROZEN.id}
               else state end
      """
    )
    for(cell <- ret._2.cellsWhere(sqls"position >= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }
  def thawUpto(position: Int)
              (implicit session: DBSession): (Branch, Workflow) =
  {
    val ret = modify(
      module = None,
      action = ActionType.FREEZE,
      prevWorkflowId = headId,
      updateState = sqls"""
          case when position <= $position 
                      and state = ${ExecutionState.FROZEN.id}
                    then ${ExecutionState.WAITING.id}
               else state end
      """,
      forceRecomputeState = true,
    )
    for(cell <- ret._2.cellsWhere(sqls"position <= ${position}")){
      DeltaBus.notifyStateChange(ret._2, cell.position, cell.state)
    }
    return ret
  }

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

  private def modify(
    module: Option[Module],
    action: ActionType.T,
    prevWorkflowId: Identifier = headId,
    updatePosition: SQLSyntax = sqls"position",
    updateState: SQLSyntax = sqls"state",
    keepCells: SQLSyntax = sqls"1=1",
    addModules: Iterable[(Identifier, Int)] = Seq(),
    forceRecomputeState: Boolean = false
  )(implicit session: DBSession): (Branch, Workflow) = 
  {
    // Note: we're working with immutable objects here.  `branch` will be the 
    // new instance with the updated headId
    val (branch, workflow) = initWorkflow(
      prevId = Some(prevWorkflowId),
      action = action,
      actionModuleId = module.map { _.id }
    )

    withSQL {
      val c = Cell.syntax
      insertInto(Cell)
        .select(
          sqls"""${workflow.id} as workflow_id""",
          updatePosition + sqls" as position",
          c.moduleId,
          c.resultId,
          updateState + sqls" as state",
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
    if(forceRecomputeState){
      Provenance.updateCellStates(workflow.cellsInOrder, Map.empty)
    }

    return (branch, workflow)
  }

  def createdFromModuleId(implicit session:DBSession): Option[Identifier] =
    createdFromWorkflowId.flatMap { Workflow.get(_).actionModuleId }

  private[vizierdb] def cloneWorkflow(workflowId: Identifier)(implicit session: DBSession): (Branch, Workflow) =
    modify(None, ActionType.CREATE, workflowId)

  def workflows(implicit session:DBSession): Seq[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.branchId, id)  
    }.map { Workflow(_) }.list.apply()

  def createdFromWorkflow(implicit session:DBSession): Option[Workflow] = 
    createdFromWorkflowId.map { Workflow.get(_) }

  def describe(implicit session:DBSession): JsObject =
    JsObject(
      summarize.value ++ Map(
        "workflows" -> Json.toJson(workflows.map { _.summarize })
      )
    )

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
             b.properties -> Option(properties).getOrElse { this.properties }.toString,
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

  def get(target: Identifier)(implicit session:DBSession): Branch = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Branch] = 
    withSQL { 
      val b = Branch.syntax 
      select
        .from(Branch as b)
        .where.eq(b.id, target) 
    }.map { apply(_) }.single.apply()

  def lookup(projectId: Identifier, branchId: Identifier)(implicit session:DBSession): Option[Branch] = 
    withSQL { 
      val b = Branch.syntax 
      select
        .from(Branch as b)
        .where.eq(b.id, branchId) 
          .and.eq(b.projectId, projectId)
    }.map { apply(_) }.single.apply()


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

