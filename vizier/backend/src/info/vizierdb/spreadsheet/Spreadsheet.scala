package info.vizierdb.spreadsheet

import scala.collection.mutable
import org.apache.spark.sql.{ Row, DataFrame, Column }
import info.vizierdb.util.RowCache
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber
import org.apache.spark.sql.types._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import org.apache.spark.sql.catalyst.expressions.Expression
import scala.util.Random
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.SparkPrimitive
import play.api.libs.json._
import org.apache.spark.sql.catalyst.expressions.{
  Literal,
  Cast
}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.catalyst.analysis.UnresolvedAttribute
import info.vizierdb.VizierException

class Spreadsheet(data: SpreadsheetDataSource)
                 (implicit ec: ExecutionContext)
  extends LazyLogging
{
  var size: Long = Await.result(data.size, Duration.Inf)

  var nextColumnId = 0l
  def getColumnId(): Long = 
    { val id = nextColumnId; nextColumnId += 1; id }

  val schema: mutable.ArrayBuffer[OutputColumn] =
    mutable.ArrayBuffer(
      data.schema
          .zipWithIndex
          .map { case (field, idx) => 
                  OutputColumn.mapFrom(idx, field, getColumnId(), idx) 
          }:_*)

  val columns = mutable.Map[Long, OutputColumn](
     schema.map { col => col.id -> col }:_*)

  val executor = 
    new SingleRowExecutor(
      // retrieve source data
      (col, row) => 
        columns(col.id).source match {
          case SourceDataset(idx, _) => data(idx, row)
          case DefaultValue(v) => Future.successful(v)
        },

      // notify callback
      (col, row) =>
        callback {
          _.refreshCell(columns(col.id).position, row)
        }
    )

  def pickCachePageToDiscard(candidates: Seq[Long], pageSize: Int): Long =
  {
    val criticalRows = 
      executor.requiredSourceRows
    val nonCriticalCandidates = 
      candidates.filter { start => (criticalRows intersect (start until start+pageSize)).isEmpty }
    if(nonCriticalCandidates.isEmpty){
      logger.warn("Warning: Row cache is full.  Performance may degrade noticeably.")
      return candidates(Random.nextInt % candidates.size)
    } else {
      return nonCriticalCandidates(Random.nextInt % nonCriticalCandidates.size)
    }
  }

  val callbacks = mutable.ArrayBuffer[SpreadsheetCallbacks]()

  private def callback(op: SpreadsheetCallbacks => Unit) = 
    callbacks.foreach(op)

  def subscriptions: Iterator[(Long, Long)] =
    RangeSet.ofIndices(
      executor.requiredSourceRows
    ).iterator

  def getRow(row: Long): Array[Option[Try[Any]]] = 
  {
    schema.map { col => 
            executor.getFuture(col.ref, row).value
          }
          .toArray
  }
  def getCell(column: Int, row: Long): Option[Try[Any]] = 
  {
    executor.getFuture(schema(column).ref, row).value
  }

  def getExpression(column: Int, row: Long): Option[Expression] =
  {
    executor.getExpression(columns(column).ref, row)
  }

  def subscribe(start: Long, count: Int): Unit = 
  {
    executor.subscribe(RangeSet(start, start+count-1))
  }

  def unsubscribe(start: Long, count: Int): Unit = 
  {
    executor.unsubscribe(RangeSet(start, start+count-1))
  }

  // def attrToRValue(attr: String, exprRow: Long): RValue =
  // {
  //   // This is presently SUUUUUPER hacky
  //   // TODO: Replace me after discussing

  //   // all of the non-digit chars
  //   val columnString = 
  //     attr.takeWhile { case x if x >= 48 && x <= 57 => false; case _ => true}
  //         .toLowerCase()
  //   val column =
  //     schema.find { _.output.name.toLowerCase == columnString }.get
  //   var rowString = 
  //     attr.drop(columnString.size)
  //   val row =
  //     try { rowString.toLong-1 } catch { case _:NumberFormatException => exprRow }

  //   SingleCell(column.ref, row)
  // }

  def parse(expression: String, row: Long): Expression =
  {
    expr(expression).expr.transform {
      case UnresolvedAttribute(Seq(attr)) => 
        schema.find { _.output.name.toLowerCase == attr }
              .map { column => 
                RValueExpression(OffsetCell(column.ref, 0))
              }
              .getOrElse {
                InvalidRValue(s"Unknown column: '$attr'")
              }
    }
  }

  def editCell(column: Int, row: Long, value: JsValue): Unit = 
  {
    assert(0 <= row && row < size, "That row doesn't exist")
    assert(0 <= column && column < schema.size, "That row doesn't exist")
    val decoded: Expression =
      value match {
        case JsString(s) if s(0) == '=' => 
          Cast(parse(s.drop(1), row), schema(column).output.dataType)
        case _ => try {
          Literal(
            SparkPrimitive.decode(value, schema(column).output.dataType, castStrings = true), 
            schema(column).output.dataType
          )
        } catch {
          case t: Throwable => 
            logger.warn(s"Difficulty parsing $value (${t.getMessage()}); falling back to spark")
            Cast(
              Literal(
                value match { case JsString(s) => s; case _ => value.toString },
                StringType
              ),
              schema(column).output.dataType
            )
        }
      }
    executor.update(SingleCell(columns(column).ref, row), decoded)
  }

  def deleteRows(start: Long, count: Int): Unit =
  {
    assert(size >= start + count, "Those rows don't exist")
    executor.deleteRows(start,count)
    size = size - count
    callback { _.refreshRows(start, start - size) }
  }

  def insertRows(start: Long, count: Int): Unit =
  {
    assert(size >= start, "Can't insert there")
    executor.insertRows(start,count)
    size = size + count
    callback { _.refreshRows(start, start - size) }
  }

  def moveRows(from: Long, to: Long, count: Int): Unit =
  {
    assert(to < from || from + count <= to, "Trying to move a group of rows to within itself")
    assert(size >= from+count, "Source rows don't exist")
    assert(size >= to, "Destination rows don't exist")
    executor.moveRows(from, to, count)
    val start = math.min(from, to)
    val end = math.max(from+count, to)
    callback { _.refreshRows(start, end - start+1) }
  }

  def indexOfColumn(name: String): Int =
    schema.indexWhere { _.output.name.equalsIgnoreCase(name) }

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
        schema(idx).position = idx
      }
      schema(toIdx) = fromValue
      schema(toIdx).position = toIdx
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
    def newCol(position: Int) = 
      OutputColumn.withDefaultValue(StructField(name, dataType), null, getColumnId(), position)
    val idx = before match {
      case None => {
        schema.append(newCol(schema.size))
        schema.size - 1
      }
      case Some(name) => {
        val idx = indexOfColumn(name)
        schema.insert(idx, newCol(idx))
        for(i <- idx+1 until schema.size){ schema(i).position = i }
        idx
      }
    }
    callback(_.refreshEverything())
  }

  /**
   * Delete a column
   * @param  name      The name of the column to delete.
   */
  def deleteColumn(name: String): Unit = 
  {
    val idx = indexOfColumn(name)
    columns.remove(schema.remove(idx).id)
    for(i <- idx until schema.size) { schema(i).position = i }

    callback(_.refreshEverything())
  }


}

object Spreadsheet
{
  type UpdateId = Long

  def apply(base: DataFrame)(implicit ec: ExecutionContext) = 
  {
    val annotated = 
      AnnotateWithSequenceNumber(base)
    val df = 
      annotated.select(
        (
          annotated(AnnotateWithSequenceNumber.ATTRIBUTE) +:
          base.columns.map { annotated(_) }
        ):_*
      )
    val seqNo = df(AnnotateWithSequenceNumber.ATTRIBUTE)
    val baseCols = base.columns.map { df(_) }

    val cache = 
      new RowCache[Array[Any]](
        fetchRows = { (offset, limit) =>
          Future {
            df.filter { (seqNo >= offset) and (seqNo < (offset+limit)) }
              .orderBy(seqNo.asc)
              .select(baseCols:_*)
              .collect()
              .map { _.toSeq.toArray }
          }
        },
        selectForInvalidation = { (candidates, pageSize) => candidates.head }
      )

    val spreadsheet =
      new Spreadsheet(
        new CachedSource(
          base.schema.fields,
          cache,
          Future { df.count() }
        )
      )
    cache.selectForInvalidation = spreadsheet.pickCachePageToDiscard _

    /* return */ spreadsheet
  }
}