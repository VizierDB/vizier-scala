package info.vizierdb.catalog

import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax
import java.time.ZonedDateTime
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import info.vizierdb.viztrails.Scheduler

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
  modified: ZonedDateTime
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
  def insert(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) =
    modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      updatePosition = sqls"case when position >= $position then position + 1 else position end",
      updateState = sqls"case when position >= $position then ${ExecutionState.WAITING.id} else state end",
      addModules = Seq(module.id -> Workflow.getLength(headId))
    )
  def update(position: Int, module: Module)(implicit session: DBSession): (Branch, Workflow) = 
    modify(
      module = Some(module),
      action = ActionType.INSERT,
      prevWorkflowId = headId,
      updateState = sqls"case when position >= $position then ${ExecutionState.WAITING.id} else state end",
      keepCells = sqls"position <> $position",
      addModules = Seq(module.id -> position)
    )

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
           .where.eq(c.workflowId, workflow.id).and(Some(keepCells))
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

  private[catalog] def cloneWorkflow(workflowId: Identifier)(implicit session: DBSession): (Branch, Workflow) =
    modify(None, ActionType.CREATE, workflowId)
}
object Branch 
  extends SQLSyntaxSupport[Branch]
{
  def apply(rs: WrappedResultSet): Branch = autoConstruct(rs, (Branch.syntax).resultName)

  def get(target: Identifier)(implicit session:DBSession): Branch = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Branch] = 
    withSQL { 
      val b = Branch.syntax 
      select
        .from(Branch as b)
        .where.eq(b.id, target) 
    }.map { apply(_) }.single.apply()
}