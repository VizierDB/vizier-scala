package info.vizierdb.spreadsheet

import info.vizierdb.types._
import org.apache.spark.sql.Column
import org.apache.spark.sql.functions._

/**
 * A trait for "lightweight" transformations over the order of rows -- simple 
 * transpositions of row indices.  See [[ReferenceFrame]].
 */
sealed trait RowTransformation
{
  /**
   * Translate the row reference forward (source to output)
   * @param   row    The row index relative to the source dataset
   * @return         The row index relative to the output dataset if it appears
   */
  def forward(row: RowReference): Option[RowReference] = 
    row match {
      case SourceRowByIndex(idx) => forward(idx).map { SourceRowByIndex(_) }
      case _ => Some(row)
    }
  /**
   * Translate the row reference forward (source to output)
   * @param   row    The row index relative to the source dataset
   * @return         The row index relative to the output dataset if it appears
   */
  def forward(row: Long): Option[Long]  
  /**
   * Translate a set of row ranges forward (source to output)
   * @param   rows   The set of rows to translate
   */
  def forward(rows: RangeSet): RangeSet

  /**
   * Translate the row reference backward (output to source)
   * @param   row    The row index relative to the output dataset
   * @return         The row index relative to the source dataset (Left)
   *                 or the insert ID and index of relative to the insert ID (Right)
   */
  def backward(row: RowReference): RowReference =
    row match {
      case SourceRowByIndex(idx) => backward(idx)
      case _ => row
    }
  /**
   * Translate the row reference backward (output to source)
   * @param   row    The row index relative to the output dataset
   * @return         The row index relative to the source dataset (Left)
   *                 or the insert ID and index of relative to the insert ID (Right)
   */
  def backward(row: Long): RowReference

  /**
   * Translate a set of row ranges backward (output to soruce)
   * @param   rows   The set of rows to translate
   */
  def backward(rows: RangeSet): RangeSet
}

case class InsertRows(position: Long, count: Int, insertId: Identifier) extends RowTransformation
{
  override def forward(row: RowReference): Option[RowReference] = 
    row match {
      case InsertedRow(insertedId, idx) if insertedId == insertId => Some(SourceRowByIndex(position + idx))
      case _ => super.forward(row)
    }

  def forward(row: Long): Option[Long] =
    if(row < position) { return Some(row) } else { return Some(row + count) }
  def forward(rows: RangeSet) = 
  {
    val (low, high) = rows.split(position)
    low ++ high.offset(count)
  }

  def backward(row: Long): RowReference =
    if(row < position) { return SourceRowByIndex(row) }
    else if(row < position + count) { return InsertedRow(insertId, (row - position).toInt) }
    else { return SourceRowByIndex(row - count) }

  def backward(rows: RangeSet): RangeSet = 
  {
    val (low, high) = rows.split(position)
    low ++ high.remove(position, position+count-1).offset(-count)    
  }
}

case class DeleteRows(position: Long, count: Int) extends RowTransformation
{
  def forward(row: Long): Option[Long] = 
    if(row < position) { Some(row) }
    else if(row < position + count) { None }
    else { Some(row - count) }
  def forward(rows: RangeSet) = 
  {
    val (low, high) = rows.split(position)
    low ++ high.remove(position, position+count-1).offset(-count)
  }
  def backward(row: Long): RowReference = 
    if(row < position) { SourceRowByIndex(row) }
    else { SourceRowByIndex(row + count) }
  def backward(rows: RangeSet): RangeSet = 
  {
    val (low, high) = rows.split(position)
    low ++ high.offset(count)
  }

}

case class MoveRows(from: Long, to: Long, count: Int) extends RowTransformation
{
  def forward(row: Long): Option[Long] =
  {
    val rowAfterDelete: Long = 
      if(row < from) { row }
      else if(row < from + count) { return Some(to + (row - from)) }
      else { row - count }

    if(rowAfterDelete < to){ Some(rowAfterDelete) }
    else { Some(rowAfterDelete + count) }
  }
  def forward(rows: RangeSet) = 
  {
    val (lowerBound, mid, upperBound) =
      if(from < to){
        (from, from+count-1, to)
      } else {
        (to, from, from+count-1)
      }

    val (lowStay, rowsA) = rows.split(lowerBound-1)
    val (lowMove, rowsB) = rowsA.split(mid)
    val (highMove, highStay) = rowsB.split(upperBound)

    lowStay ++ lowMove.offset(mid - lowerBound) ++ highMove.offset(lowerBound - mid) ++ highStay
  }

  def backward(row: Long): RowReference = 
  {
    val rowBeforeInsert: Long =
      if(row < to) { row }
      else if(row < to + count) { return SourceRowByIndex(from + (row - to)) }
      else { row - count }

    if(rowBeforeInsert < from){ SourceRowByIndex(rowBeforeInsert) }
    else { SourceRowByIndex(rowBeforeInsert + count) }
  }

  def backward(rows: RangeSet) = 
  {
    val (lowerBound, mid, upperBound) =
      if(from < to){
        (from, to, to+count-1)
      } else {
        (to, to+count-1, from)
      }

    val (lowStay, rowsA) = rows.split(lowerBound-1)
    val (lowMove, rowsB) = rowsA.split(mid)
    val (highMove, highStay) = rowsB.split(upperBound)

    lowStay ++ lowMove.offset(mid - lowerBound) ++ highMove.offset(lowerBound - mid) ++ highStay
  }
}