package info.vizierdb.catalog

import scalikejdbc._

import info.vizierdb.types._
import info.vizierdb.catalog.binders._
import java.time.ZonedDateTime
import info.vizierdb.VizierException

/**
 * One cell in a workflow.  
 * 
 * Broadly, a cell is a Many/Many relationship between Workflow and Module. Each cell is identified 
 * by its parent workflow and a unique, contiguous, zero-indexed position in that workflow.
 *
 * The cell is parameterized by a Module definition (referenced by moduleId), and may optionally
 * point to a Result (referenced by resultId).
 *
 * Cells are immutable once created, with the exception of the resultId and state fields.  Both
 * of these fields are intended to conform to monotonicity guarantees.
 *
 * state adopts conforms the following state diagram
 * ```
 *
 * Clone Cell
 *          \
 *           v
 *     --- WAITING -----------> DONE
 *    /       |                  ^
 *   /        v                  |
 *   |     BLOCKED ---+-> ERROR  |
 *   |        |      /           /
 *    \       v     /           /
 *     `--> STALE -+-----------`
 *            ^
 *           /
 *   New Cell
 * 
 * ```
 * The value of resultId depends on the current state.
 * - WAITING: resultId references the [[Result]] from the previous execution of this cell.  Note 
 *            that the corresponding result may or may not be valid.  If the cell transitions to 
 *            the DONE state without going through the BLOCKED or STALE states, resultId will remain
 *            unchanged.
 * - BLOCKED or STALE: resultId is invalid and should be ignored.
 * - ERROR: resultId is either None (a preceding cell triggered the error) or Some(result) with a
 *          result object describing the error
 * - DONE: resultId references the result of the execution
 *
 * In summary resultId should usually be ignored in all states except ERROR and DONE.
 */
case class Cell(
  workflowId: Identifier,
  position: Int,
  moduleId: Identifier,
  resultId: Option[Identifier],
  state: ExecutionState.T,
  created: ZonedDateTime
)
{
  def module(implicit session: DBSession) = Module.get(moduleId)
  def result(implicit session: DBSession) = resultId.map { Result.get(_) }

  def inputs(implicit session: DBSession): Seq[ArtifactRef] = 
    withSQL { 
      val i = InputArtifactRef.syntax
      select.from(InputArtifactRef as i).where.eq(i.resultId, resultId)
    }.map { InputArtifactRef(_) }.list.apply()
  def outputs(implicit session: DBSession): Seq[ArtifactRef] = 
    withSQL { 
      val o = OutputArtifactRef.syntax
      select.from(OutputArtifactRef as o).where.eq(o.resultId, resultId)
    }.map { OutputArtifactRef(_) }.list.apply()
  def messages(implicit session: DBSession):Iterable[Message] = 
    withSQL { 
      val m = Message.syntax
      selectFrom(Message as m).where.eq(m.resultId, resultId)
    }.map { Message(_) }.list.apply()
  def successors(implicit session: DBSession): Seq[Cell] = 
    withSQL { 
      val c = Cell.syntax
      select.from(Cell as c)
            .where.eq(c.workflowId, workflowId).and.gt(c.position, position)
            .orderBy(c.position.asc)
    }.map { Cell(_) }.list.apply()

  def projectId(implicit session: DBSession): Identifier = 
    withSQL {
      val w = Workflow.syntax
      val b = Branch.syntax
      select(b.projectId)
          .from(Workflow as w)
          .join(Branch as b)
          .where.eq(w.branchId, b.id)
            .and.eq(w.id, workflowId)
    }.map { _.get[Identifier](1)}.single.apply().get

  def start(implicit session: DBSession): (Cell, Result) = 
  {
    val newResultId = withSQL {
      val r = Result.column
      insertInto(Result).
        namedValues(r.started -> ZonedDateTime.now())
    }.updateAndReturnGeneratedKey.apply()
    withSQL {
      val c = Cell.column
      update(Cell)
        .set(c.resultId -> newResultId)
        .where.eq(c.workflowId, workflowId)
          .and.eq(c.position, position)
    }.update.apply()
    return (copy(resultId = Some(newResultId)), Result.get(newResultId))
  }
  def finish(state: ExecutionState.T)(implicit session: DBSession): (Cell, Result) = 
  {
    val resultId = this.resultId.getOrElse {
      throw new VizierException("Attempting to finish un-started cell $this")
    }
    val newCell = updateState(state)
    withSQL { 
      val r = Result.column
      update(Result)
        .set(r.finished -> ZonedDateTime.now())
        .where.eq(r.id, resultId)
    }.update.apply()

    return (newCell, Result.get(resultId))
  }
  def updateState(state: ExecutionState.T)(implicit session: DBSession): Cell =
  {
    withSQL { 
      val c = Cell.column
      update(Cell)
        .set(c.state -> state)
        .where.eq(c.workflowId, workflowId)
          .and.eq(c.position, position)
    }.update.apply()
    copy(state = state)
  }

  override def toString = s"Workflow $workflowId @ $position: Module $moduleId ($state)"
}
object Cell 
  extends SQLSyntaxSupport[Cell]
{
  def apply(rs: WrappedResultSet): Cell = autoConstruct(rs, (Cell.syntax).resultName)
  override def columns = Schema.columns(table)

  def get(workflowId: Identifier, position: Int)(implicit session:DBSession): Cell = lookup(workflowId, position).get
  def lookup(workflowId: Identifier, position: Int)(implicit session:DBSession): Option[Cell] = 
    withSQL { 
      val c = Cell.syntax 
      select
        .from(Cell as c)
        .where.eq(c.workflowId, workflowId).and.eq(c.position, position)
    }.map { apply(_) }.single.apply()
}