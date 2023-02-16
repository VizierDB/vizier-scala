package info.vizierdb.spreadsheet

import org.apache.spark.sql.Column
import play.api.libs.json._

case class ColumnRef(id: Long)
{
  var label: String = null
  def apply(row: Long) = SingleCell(this, row)
  def apply(from: Long, to: Long) = ColumnRange(this, from, to)
  def offsetBy(by: Int) = OffsetCell(this, by)

  override def toString = Option(label).getOrElse { id.toString }
}
object ColumnRef
{
  implicit val format: Format[ColumnRef] = Json.format
  def apply(id: Long, label: String): ColumnRef = 
  {
    val ret = ColumnRef(id)
    ret.label = label
    return ret
  }
}

/**
 * An 'lvalue'; a valid assignment **target**
 * 
 * May be:
 * - [[SingleCell]]: A reference to a sepcific cell
 * - [[ColumnRange]]: A reference to a range of cells being assigned to
 * - [[FullColumn]]: Like [[ColummRange]] but the entire column
 */
sealed trait LValue
{
  def column: ColumnRef
  def toRangeSet: RangeSet
  def offsetLBy(offset: Long): LValue
}

/**
 * An 'rvalue'; a reference to a cell in the context of another cell being evaluated.
 * 
 * May be:
 * - [[SingleCell]]: A reference to a specific cell
 * - [[OffsetCell]]: A reference to a cell in another row offset by some number of rows in the current reference frame.
 */ 
sealed trait RValue
{ 
  def column: ColumnRef 

  def expr = RValueExpression(this)
  def ref = new Column(expr)
}
case class SingleCell(column: ColumnRef, row: Long) extends LValue with RValue
{
  def offsetLBy(offset: Long): LValue = 
    copy(row = row + offset)
  def toRangeSet: RangeSet = RangeSet(row, row)
  override def toString =
    s"[${column}:$row]"
}
case class ColumnRange(column: ColumnRef, from: Long, to: Long) extends LValue
{
  def offsetLBy(offset: Long): LValue = 
    copy(from = from + offset, to = to + offset)
  def toRangeSet: RangeSet = RangeSet(from, to)
}
case class OffsetCell(column: ColumnRef, rowOffset: Int) extends RValue
