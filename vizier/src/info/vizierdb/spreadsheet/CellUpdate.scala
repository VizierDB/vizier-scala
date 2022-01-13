package info.vizierdb.spreadsheet

import org.apache.spark.sql.catalyst.expressions._

trait CellUpdate
{
  val target: LValue
  val expression: Expression
  val frame: ReferenceFrame

  def rvalues: Seq[RValue] =
    expression.collect {
      case RValueExpression(rvalue, _) => rvalue
      case UnresolvedRValueExpression(rvalue) => rvalue
    }

  /**
   * Compute the set of source ranges that would affect an update
   * to the provided target expression when computing its value for
   * rows in the provided range.
   * @param    from         The first row that this update is used to compute
   * @param    to           The last row that this update is used to compute
   * @param    targetFrame  The [[ReferenceFrame]] in which from/to are specified
   * @return                A list of [[ColumnRef]], [[RangeSet]] tuples 
   *                        identifying the cells that the affected update cells 
   *                        depends on.
   */
  def affectedRanges(from: Long, to: Long, targetFrame: ReferenceFrame = frame): Map[ColumnRef, RangeSet] =
  {
    val offsetFrame = targetFrame.relativeTo(frame)
    val baseRange = offsetFrame.backward(RangeSet(from, to))
    rvalues.map { 
              case SingleCell(col, row) => col -> RangeSet(row, row)
              case OffsetCell(col, rowOffset) => col -> baseRange.offset(rowOffset)
            }
           .groupBy { _._1 }
           .mapValues { ranges =>
              offsetFrame.forward(
                ranges.map { _._2 }
                      .foldLeft(RangeSet()){ _ ++ _ }
              ) 
            }
  }

  /**
   * Compute the ranges for which this update needs to be recomputed given
   * that one or more of its input rows were invalidated.
   * @param   from         The first row that was invalidated
   * @param   to           The last row that was invalidated
   * @param   column       The column in which rows were invalidated
   * @param   targetFrame  The reference frame of from and to
   * @return               The set of rows on which this update needs to be
   *                       re-evaluated.
   */
  def computeInvalidation(from: Long, to: Long, column: ColumnRef, targetFrame: ReferenceFrame = frame): RangeSet =
  {
    val offsetFrame = targetFrame.relativeTo(frame)
    val baseRange = offsetFrame.backward(RangeSet(from, to))
    offsetFrame.forward(
      rvalues.filter { _.column == column }
             .map { 
                case SingleCell(_, row) => target.toRangeSet
                case OffsetCell(_, rowOffset) => baseRange.offset(-rowOffset).intersect(target.toRangeSet)
             }
             .foldLeft(RangeSet()) { _ ++ _ }
    )
  }
}


case class UpdateSomeCells(
  target: TargettedLValue,
  expression: Expression,
  frame: ReferenceFrame
) extends CellUpdate

case class UpdateAllCells(
  target: FullColumn,
  expression: Expression,
  frame: ReferenceFrame
) extends CellUpdate