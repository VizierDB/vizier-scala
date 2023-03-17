package info.vizierdb.spreadsheet


import scala.collection.mutable


/**
 * The target of a trigger
 *
 * May be:
 * - [[ColumnRange]]: A range of cells depend on a single cell
 * - [[OffsetCell]]: A range of cells depend on a range of cells
 */
sealed trait TriggerTarget
{
  def col: ColumnRef
}

case class AbsoluteTrigger(col: ColumnRef, rows: RangeSet, frame: ReferenceFrame) extends TriggerTarget
case class RelativeTrigger(col: ColumnRef, offset: Int, frame: ReferenceFrame) extends TriggerTarget
