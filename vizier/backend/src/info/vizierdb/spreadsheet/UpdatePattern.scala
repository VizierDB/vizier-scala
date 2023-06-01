package info.vizierdb.spreadsheet

import org.apache.spark.sql.catalyst.expressions.Expression
import play.api.libs.json._
import org.apache.spark.sql.functions.expr

case class UpdatePattern(
  expression: Expression,
  id: Long
)
{
  /**
   * Returns true if this cell's expression involves exclusively references
   * to the local row
   */
  def isLocal = 
    rvalues.forall { 
      case _:SingleCell => true
      case OffsetCell(_, 0) => true
      case OffsetCell(_, _) => false
    }

  def rvalues: Seq[RValue] =
    expression.collect {
      case RValueExpression(rvalue) => rvalue
    }

  /**
   * Compute the set of source ranges that would affect an update
   * to the provided target expression when computing its value for
   * rows in the provided range.
   * @param    targetRows   The range of rows of cells that this update is
   *                        used to compute
   * @return                A list of [[ColumnRef]], [[RangeSet]] tuples 
   *                        identifying the cells that those cells to which
   *                        this update has been applied depend.
   */
  def upstreamCells(targetCellRows: RangeSet): Map[ColumnRef, RangeSet] =
  {
    rvalues.map { 
              case SingleCell(col, row) => 
                col -> RangeSet(row, row)
              case OffsetCell(col, rowOffset) => 
                col -> targetCellRows.offset(rowOffset)
            }
           .groupBy { _._1 }
           .mapValues { ranges =>
              ranges.map { _._2 }
                    .foldLeft(RangeSet()){ _ ++ _ }
            }
  }

  /**
   * Compute the ranges for which this update needs to be recomputed given
   * that one or more of its input rows were invalidated.
   * @param   targetCellRows
   * @param   upstreamCellColumn The column in which cells were invalidated
   * @param   upstreamCellRows   The rows of cells that were invalidated
   * @return                     A RangeSet of rows for which this update
   *                             that the invalidated cells could affect.
   * 
   * This method computes the set of cell rows that an update to 
   * upstreamCellColumn[upstreamCells] could affect, assuming that
   * the affected cells are on the rows identified by targetCellRows
   */
  def recomputedSlice(targetCellRows: RangeSet, upstreamCellColumn: ColumnRef, upstreamCellRows: RangeSet): RangeSet =
  {
    rvalues.filter { _.column == upstreamCellColumn }
           .map { 
              case SingleCell(_, row) => 
                if(upstreamCellRows(row)){
                  // If the single cell is in the affected region, all
                  // of the target cells are invalidated.
                  targetCellRows
                } else {
                  // If the single cell is not in the affected region,
                  // then nothing happens.
                  RangeSet()
                }
              case OffsetCell(_, rowOffset) => 
                // If we have an offset reference to e.g., -1 row, then
                // if rows 4, 5, and 6 are affected, then rows 5, 6, 
                // and 7 should be invalidated (-offset).  Crop the 
                // potentially invalidated rows to the subset of rows
                // that are actually affected by this update.
                upstreamCellRows.offset(-rowOffset)
                                .intersect(targetCellRows)
           }
           .foldLeft(RangeSet()) { _ ++ _ }
  }

  override def toString = s"{${expression.toString}}[$id]"
}

object UpdatePattern
{
  implicit val expressionFormat = Format[Expression](
    new Reads[Expression] {
      def reads(json: JsValue): JsResult[Expression] = 
        JsSuccess(expr(json.as[String]).expr)
    },
    new Writes[Expression] {
      def writes(o: Expression): JsValue = 
        JsString(o.toString())
    }
  )
  implicit val format: Format[UpdatePattern] = Json.format
}