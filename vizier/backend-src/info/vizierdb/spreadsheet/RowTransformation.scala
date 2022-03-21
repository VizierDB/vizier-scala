package info.vizierdb.spreadsheet
import play.api.libs.json._
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
      case RowByIndex(idx) => forward(idx).map { RowByIndex(_) }
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
      case RowByIndex(idx) => backward(idx)
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
      case InsertedRow(insertedId, idx) if insertedId == insertId => Some(RowByIndex(position + idx))
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
    if(row < position) { return RowByIndex(row) }
    else if(row < position + count) { return InsertedRow(insertId, (row - position).toInt) }
    else { return RowByIndex(row - count) }

  def backward(rows: RangeSet): RangeSet = 
  {
    val (low, high) = rows.split(position)
    low ++ high.remove(position, position+count-1).offset(-count)    
  }
}
object InsertRows
{
  implicit val insertRowsFormat = Json.format[InsertRows]
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
    if(row < position) { RowByIndex(row) }
    else { RowByIndex(row + count) }
  def backward(rows: RangeSet): RangeSet = 
  {
    val (low, high) = rows.split(position)
    low ++ high.offset(count)
  }

}
object DeleteRows
{
  implicit val deleteRowsFormat = Json.format[DeleteRows]
}

case class MoveRows(from: Long, to: Long, count: Int) extends RowTransformation
{
  def forward(row: Long): Option[Long] =
  {
    if(from < to){
      if(row < from || row >= to){ return Some(row) }
      else if(row < from + count) { return Some(row + to - from - count) }
      else { return Some(row - count) }
    } else {
      if(row < to || row >= from + count){ return Some(row) }
      else if(row < from) { return Some(row + count) }
      else { return Some(row + to - from - count) }
    }
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
    if(from < to){
      if(row < from || row >= to){ return RowByIndex(row) }
      else if(row < to - count) { return RowByIndex(row + from - to - count) }
      else { return RowByIndex(row + count) }
    } else {
      if(row < to || row >= from + count){ return RowByIndex(row) }
      else if(row >= to + count) { return RowByIndex(row - count) }
      else { return RowByIndex(row + from - to - count) }
    }
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
object MoveRows
{
  implicit val moveRowsFormat: Format[MoveRows] = Json.format
}