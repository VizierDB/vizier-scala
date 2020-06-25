package info.vizierdb.catalog

import scalikejdbc._
import java.time.ZonedDateTime
import info.vizierdb.types._
import info.vizierdb.catalog.binders._

/**
 * One version of a workflow.  
 *
 * The workflow and its cells are mostly immutable once created with one exception.  The aborted
 * field should preserve a monotonicity guarantee (False -> True)
 */
case class Workflow(
  id: Identifier,
  prevId: Option[Identifier],
  branchId: Identifier,
  action: ActionType.T,
  actionModuleId: Option[Identifier],
  created: ZonedDateTime,
  aborted: Boolean
)
{
  def cells(implicit session: DBSession): Seq[Cell] = 
    withSQL {
      val c = Cell.syntax
      select
        .from(Cell as c)
        .where.eq(c.workflowId, id)
    }.map { Cell(_) }.list.apply()
  def cellsInOrder(implicit session: DBSession): Seq[Cell] = 
    withSQL {
      val c = Cell.syntax
      select
        .from(Cell as c)
        .where.eq(c.workflowId, id)
        .orderBy(c.position)
    }.map { Cell(_) }.list.apply()
  def modules(implicit session: DBSession): Seq[Module] = 
    withSQL {
      val c = Cell.syntax
      val m = Module.syntax
      select(m.resultAll)
        .from(Cell as c)
        .join(Module as m)
        .where.eq(c.workflowId, id)
          .and.eq(m.id, c.moduleId)
    }.map { Module(_) }.list.apply()
  def modulesInOrder(implicit session: DBSession): Seq[Module] = 
    withSQL {
      val c = Cell.syntax
      val m = Module.syntax
      select(m.resultAll)
        .from(Cell as c)
        .join(Module as m)
        .where.eq(c.workflowId, id)
          .and.eq(m.id, c.moduleId)
        .orderBy(c.position)
    }.map { Module(_) }.list.apply()
  def length(implicit session: DBSession): Int = Workflow.getLength(id)
  def abort(implicit session:DBSession): Workflow =
  {
    withSQL {
      val w = Workflow.column
      update(Workflow)
        .set(w.aborted -> 1)
        .where.eq(w.id, id)
    }.update.apply()
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(c.state -> ExecutionState.ERROR)
        .where.ne(c.state, ExecutionState.DONE)
          .and.eq(c.workflowId, id)
    }.update.apply()
    copy(aborted = true)
  }
}
object Workflow 
  extends SQLSyntaxSupport[Workflow]
{
  def apply(rs: WrappedResultSet): Workflow = autoConstruct(rs, (Workflow.syntax).resultName)
  override def columns = Schema.columns(table)

  def getLength(workflowId: Identifier)(implicit session:DBSession) =
    sql"select max(position) from cell where workflow_id = $workflowId"
      .map { _.intOpt(1).map { _ + 1 } }.single.apply().flatten.getOrElse { 0 }

  def get(target: Identifier)(implicit session:DBSession): Workflow = lookup(target).get
  def lookup(target: Identifier)(implicit session:DBSession): Option[Workflow] = 
    withSQL { 
      val w = Workflow.syntax 
      select
        .from(Workflow as w)
        .where.eq(w.id, target)  
    }.map { apply(_) }.single.apply()

}