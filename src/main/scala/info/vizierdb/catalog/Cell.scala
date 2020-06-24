package info.vizierdb.catalog

import scalikejdbc._

import info.vizierdb.types._
import info.vizierdb.catalog.binders._

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
class Cell(
  val workflowId: Identifier,
  val position: Int,
  val moduleId: Identifier,
  var resultId: Option[Identifier],
  var state: ExecutionState.T
)
{
  def module(implicit session: DBSession) = Module.get(moduleId)
}
object Cell 
  extends SQLSyntaxSupport[Cell]
{
  def apply(rs: WrappedResultSet): Cell = autoConstruct(rs, (Cell.syntax).resultName)

  def get(workflowId: Identifier, position: Int)(implicit session:DBSession): Cell = lookup(workflowId, position).get
  def lookup(workflowId: Identifier, position: Int)(implicit session:DBSession): Option[Cell] = 
    withSQL { 
      val c = Cell.syntax 
      select
        .from(Cell as c)
        .where.eq(c.workflowId, workflowId).and.eq(c.position, position)
    }.map { apply(_) }.single.apply()
}