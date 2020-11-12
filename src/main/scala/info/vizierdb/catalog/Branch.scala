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
    modify(
      module = Some(module),
      action = ActionType.APPEND,
      prevWorkflowId = headId,
      addModules = Seq(module.id -> Workflow.getLength(headId))
    )
  def append(packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    append(Module.make(packageId, commandId)(args:_*))
  def insert(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) =
    modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      updatePosition = sqls"case when position >= $position then position + 1 else position end",
      updateState = sqls"case when position >= $position then ${ExecutionState.WAITING.id} else state end",
      addModules = Seq(module.id -> position)
    )
  def insert(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    insert(position, Module.make(packageId, commandId)(args:_*))
  def update(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) = 
    modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      updateState = sqls"case when position >= $position then ${ExecutionState.WAITING.id} else state end",
      keepCells = sqls"position <> $position",
      addModules = Seq(module.id -> position)
    )
  def update(position: Int, packageId: String, commandId: String)
            (args: (String, Any)*)
            (implicit session: DBSession): (Branch, Workflow) =
    update(position, Module.make(packageId, commandId)(args:_*))

  private[catalog] def initWorkflow(
    prevId: Option[Identifier] = None,
    action: ActionType.T = ActionType.CREATE,
    actionModuleId: Option[Identifier] = None,
    setHead: Boolean = true
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
          w.created -> now,
          w.aborted -> false
        )
    }.updateAndReturnGeneratedKey.apply()
    val workflow = Workflow.get(workflowId)

    if(setHead) { 
      val now = ZonedDateTime.now()
      val b = Branch.column
      withSQL {
        scalikejdbc.update(Branch)
          .set(b.modified -> now, b.headId -> workflowId)
          .where.eq(b.id, id)
      }.update.apply()
      return (copy(headId = workflowId, modified = now), workflow)
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
    addModules: Iterable[(Identifier, Int)] = Seq()
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
        ) {
          _.from(Cell as c)
           .where.eq(c.workflowId, headId).and(Some(keepCells))
        }
    }.update.apply()
    for((moduleId, position) <- addModules){
      val c = Cell.column
      withSQL {
        insertInto(Cell)
          .namedValues(
            c.workflowId -> workflow.id,
            c.position -> position,
            c.moduleId -> moduleId,
            c.resultId -> None,
            c.state -> ExecutionState.STALE
          )
      }.update.apply()
    }

    return (branch, workflow)
  }

  def createdFromModuleId(implicit session:DBSession): Option[Identifier] =
    createdFromWorkflowId.flatMap { Workflow.get(_).actionModuleId }

  private[catalog] def cloneWorkflow(workflowId: Identifier)(implicit session: DBSession): (Branch, Workflow) =
    modify(None, ActionType.CREATE, workflowId)

  def workflows(implicit session:DBSession): Seq[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.branchId, id)  
    }.map { Workflow(_) }.list.apply()


  def describe(implicit session:DBSession): JsObject =
    JsObject(
      summarize.value ++ Map(
        "workflows" -> JsArray(workflows.map { _.summarize })
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
      "properties"     -> properties,
      HATEOAS.LINKS    -> HATEOAS(
        HATEOAS.SELF           -> VizierAPI.urls.getBranch(projectId.toString, id.toString),
        HATEOAS.BRANCH_DELETE  -> VizierAPI.urls.deleteBranch(projectId.toString, id.toString),
        HATEOAS.BRANCH_HEAD    -> VizierAPI.urls.getBranchHead(projectId.toString, id.toString),
        HATEOAS.BRANCH_UPDATE  -> VizierAPI.urls.updateBranch(projectId.toString, id.toString),
      ),
    )
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

  def lookup(projectID: Identifier, branchId: Identifier)(implicit session:DBSession): Option[Branch] = 
    withSQL { 
      val b = Branch.syntax 
      select
        .from(Branch as b)
        .where.eq(b.id, branchId) 
          .and.eq(b.projectId, projectID)
    }.map { apply(_) }.single.apply()

}