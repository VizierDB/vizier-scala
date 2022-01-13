package info.vizierdb.spreadsheet

import org.apache.spark.sql.Column

case class ColumnRef(id: Long)
{
  def apply(row: Long) = SingleCell(this, row)
  def apply(from: Long, to: Long) = ColumnRange(this, from, to)
  def all = FullColumn(this)
  def offsetBy(by: Int) = OffsetCell(this, by)
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
}

/**
 * An LValue that refers to a specific set of rows.  Specifically excludes
 * FullColumn
 */
sealed trait TargettedLValue extends LValue
{
  def offsetLBy(offset: Long): TargettedLValue
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

  def expr = UnresolvedRValueExpression(this)
  def ref = new Column(expr)
}
case class SingleCell(column: ColumnRef, row: Long) extends TargettedLValue with RValue
{
  def offsetLBy(offset: Long): TargettedLValue = 
    copy(row = row + offset)
  def toRangeSet: RangeSet = RangeSet(row, row)
}
case class ColumnRange(column: ColumnRef, from: Long, to: Long) extends TargettedLValue
{
  def offsetLBy(offset: Long): TargettedLValue = 
    copy(from = from + offset, to = to + offset)
  def toRangeSet: RangeSet = RangeSet(from, to)
}
case class FullColumn(column: ColumnRef) extends LValue
{
  def toRangeSet: RangeSet = RangeSet(0, Long.MaxValue)
}
case class OffsetCell(column: ColumnRef, rowOffset: Int) extends RValue
