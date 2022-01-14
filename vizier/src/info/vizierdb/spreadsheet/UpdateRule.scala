package info.vizierdb.spreadsheet

import org.apache.spark.sql.catalyst.expressions.Expression

case class UpdateRule(
  expression: Expression,
  frame: ReferenceFrame,
  id: Long
)
{
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
  def triggeringRanges(from: Long, to: Long, targetFrame: ReferenceFrame = frame): Map[ColumnRef, RangeSet] =
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
   * All cells that trigger re-execution of the specified cell with this rule
   * @param  target      The row on which this rule is evaluated
   * @param  targetFrame The [[ReferenceFrame]] in which target is specified
   * @return             A sequence of cells, relative to targetFrame, that can,
   *                     if modified, invalidate the cell this rule is used to 
   *                     compute.
   */
  def triggeringCells(target: Long, targetFrame: ReferenceFrame = frame): Seq[SingleCell] =
  {
    val offsetFrame = targetFrame.relativeTo(frame)
    val baseTarget = offsetFrame.backward(SourceRowByIndex(target))
                                // We should never update a cell **prior**
                                // to it being inserted.
                                .asInstanceOf[SourceRowByIndex]
                                .idx
    rvalues.flatMap { 
      case SingleCell(col, row) => 
        offsetFrame.forward(SourceRowByIndex(row))
                   .map { _.asInstanceOf[SourceRowByIndex].idx }
                   .map { SingleCell(col, _) }
      case OffsetCell(col, rowOffset) => 
        offsetFrame.forward(SourceRowByIndex(baseTarget+rowOffset))
                   .map { _.asInstanceOf[SourceRowByIndex].idx }
                   .map { SingleCell(col, _) }
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
  def triggeredRanges(from: Long, to: Long, column: ColumnRef, targetFrame: ReferenceFrame = frame): RangeSet =
  {
    val offsetFrame = targetFrame.relativeTo(frame)
    val baseRange = offsetFrame.backward(RangeSet(from, to))
    offsetFrame.forward(
      rvalues.filter { _.column == column }
             .map { 
                case SingleCell(_, row) => baseRange
                case OffsetCell(_, rowOffset) => baseRange.offset(-rowOffset)
             }
             .foldLeft(RangeSet()) { _ ++ _ }
    )
  }
}