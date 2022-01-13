package info.vizierdb.spreadsheet

import scala.collection.mutable
import org.apache.spark.sql.{ Row, DataFrame, Column }
import info.vizierdb.util.RowCache
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber
import org.apache.spark.sql.types._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import _root_.com.esotericsoftware.kryo.io.Output

class Spreadsheet(base: DataFrame)(implicit ec: ExecutionContext)
{
  assert(base.schema(0).name == AnnotateWithRowIds.ATTRIBUTE)
  assert(base.schema(1).name == AnnotateWithSequenceNumber.ATTRIBUTE)

  var size = Future { base.count() }

  val schema: mutable.ArrayBuffer[OutputColumn] =
    mutable.ArrayBuffer(base.schema.drop(2)
                                   .zipWithIndex
                                   .map { case (field, idx) => OutputColumn.mapFrom(idx + 2, field) }:_*)

  var rf = ReferenceFrame(Seq.empty)

  val rows = new RowCache[Seq[Any]](
    fetchRows = { (offset, limit) => 
      Future { 
        base.filter {
          base(AnnotateWithSequenceNumber.ATTRIBUTE).between(offset, offset+limit)
        }.collect()
         .map { _.toSeq }
      }
    },
    selectForInvalidation = { _.head }
  )
  rows.onRefresh.append { (limit, offset) => callback { _.refreshRows(limit, offset) }}

  val insertedRows = mutable.TreeMap[(Long, Int), Array[Any]]()

  val callbacks = mutable.ArrayBuffer[SpreadsheetCallbacks]()

  def callback(op: SpreadsheetCallbacks => Unit) = 
    callbacks.foreach(op)

  def indexOfColumn(col: String): Int =
  {
    val idx = schema.indexWhere { _.output.name == col }
    assert(idx >= 0, s"Column $col does not exist")
    return idx
  }

  /**
   * Rename a column
   * @param   from  The current name of the column
   * @param   to    The name to rename the column to (must be unique)
   */
  def renameColumn(from: String, to: String): Unit =
  {
    if(from == to){ return }
    assert(!schema.exists { _.output.name == to }, s"There is already a column named $to")
    val idx = indexOfColumn(from)
    schema(idx).rename(to)
    callback(_.refreshHeaders())
  }

  /**
   * Move a column
   * @param  from     The name of the column to move
   * @param  toBefore None to move the column to the rightmost position, or the 
   *                  name of the column to move the column to the left of.
   */
  def moveColumn(from: String, toBefore: Option[String]): Unit =
  {
    val fromIdx = indexOfColumn(from)
    val toIdx = toBefore.map { indexOfColumn(_) }.getOrElse { schema.size-1 }

    val offset = if(fromIdx > toIdx){ -1 } else { 1 }
    {
      val fromValue = schema(fromIdx)
      for(idx <- fromIdx.until(toIdx, offset)){
        schema(idx) = schema(idx+offset)
      }
      schema(toIdx) = fromValue
    }

    insertedRows.mapValues { row =>
      val fromValue = row(fromIdx)
      for(idx <- fromIdx.until(toIdx, offset)){
        row(idx) = row(idx+offset)
      }
      row(toIdx) = fromValue
    }

    callback(_.refreshEverything())
  }

  /**
   * Insert a column
   * @param  from      The name of the column to insert
   * @param  before    None to insert the column at the rightmost position, or 
   *                   the name of the column to insert to the left of.
   * @param  dataType  (optional) the data type of the new column
   */
  def insertColumn(name: String, before: Option[String], dataType: DataType = StringType): Unit =
  {
    val newCol = OutputColumn.withDefaultValue(StructField(name, dataType), null)
    val idx = before match {
      case None => {
        schema.append(newCol)
        schema.size - 1
      }
      case Some(name) => {
        val idx = indexOfColumn(name)
        schema.insert(idx, newCol)
        idx
      }
    }
    insertedRows.mapValues { row => (row.take(idx) ++ Seq(null) ++ row.drop(idx)).toArray }
    callback(_.refreshEverything())
  }

  /**
   * Delete a column
   * @param  name      The name of the column to delete.
   */
  def deleteColumn(name: String): Unit = 
  {
    val idx = indexOfColumn(name)
    schema.remove(idx)

    insertedRows.mapValues { row => (row.take(idx) ++ row.drop(idx+1)).toArray }

    callback(_.refreshEverything())
  }


}

object Spreadsheet
{


  def apply(base: DataFrame)(implicit ec: ExecutionContext) = 
  {
    val annotated = 
      AnnotateWithSequenceNumber(AnnotateWithRowIds(base))
    new Spreadsheet(
      annotated.select(
        (
          annotated(AnnotateWithSequenceNumber.ATTRIBUTE) +:
          annotated(AnnotateWithRowIds.ATTRIBUTE) +:
          base.columns.map { annotated(_) }
        ):_*
      )
    )
  }
  // def apply() = new Spreadsheet(base)
}