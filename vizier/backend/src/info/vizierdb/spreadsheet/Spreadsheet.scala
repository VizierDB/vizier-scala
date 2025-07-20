/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
import info.vizierdb.spark.DataFrameOps

class Spreadsheet(
  val data: SpreadsheetDataSource,
  val schema: mutable.ArrayBuffer[OutputColumn]
)(implicit ec: ExecutionContext)
  extends LazyLogging
{
  var size: Long = Await.result(data.size, Duration.Inf)

  var nextColumnId = schema.map { _.id }.max + 1
  def getColumnId(): Long = 
    { val id = nextColumnId; nextColumnId += 1; id }

  val columns = mutable.Map[Long, OutputColumn](
     schema.map { col => col.id -> col }:_*)

  val executor = 
    {
      val ex = 
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
      for(col <- schema){ ex.addColumn(col.ref) }
      ex
    }

  def this(data: SpreadsheetDataSource)(implicit ec: ExecutionContext) =
  {
    this(data = data, 
         schema = mutable.ArrayBuffer(
            data.schema
                .zipWithIndex
                .map { case (field, idx) => 
                        OutputColumn.mapFrom(idx, field, idx, idx) 
                }:_*
          )
    )
  }

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
    assert(0 <= row && row < size, "That row doesn't exist")
    schema.map { col => 
            executor.getFuture(col.ref, row).value
          }
          .toArray
  }
  def getCell(column: Int, row: Long, notify: Boolean = false): Option[Try[Any]] = 
  {
    assert(0 <= row && row < size, "That row doesn't exist")
    val fut = executor.getFuture(schema(column).ref, row)
    if(!fut.isCompleted && notify){ 
      fut.onComplete { result => callback( _.refreshCell(column, row) ) }
    }
    return fut.value
  }

  def getExpression(column: Int, row: Long): Option[Expression] =
  {
    if(row >= size){ throw new VizierException(s"Request for undefined row at position $row (only $size rows)") }
    executor.getExpression(columns(column).ref, row)
  }

  def subscribe(start: Long, count: Int): Unit = 
  {
    if(start >= size) { return }
    executor.subscribe(RangeSet(start, Math.min(start+count-1, size-1)))
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

  def parse(expression: String): Expression =
    Spreadsheet.bindVariables(expr(expression).expr, schema)

  def editCell(column: Int, row: Long, value: JsValue): Unit = 
  {
    assert(0 <= row && row < size, "That row doesn't exist")
    assert(0 <= column && column < schema.size, "That row doesn't exist")
    val decoded: Expression =
      value match {
        case JsString(s) if s(0) == '=' => 
          Cast(parse(s.drop(1)), schema(column).output.dataType)
        case _ => try {
          Cast(
            lit(
              SparkPrimitive.decode(value, schema(column).output.dataType, castStrings = true)
            ).expr,
            schema(column).output.dataType
          )
        } catch {
          case t: Throwable => 
            logger.warn(s"Difficulty parsing $value (${t.getMessage()}); falling back to spark")
            Cast(
              lit(
                value match { case JsString(s) => s; case _ => value.toString },
              ).expr,
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
        /* return */ schema.size - 1
      }
      case Some(name) => {
        val idx = indexOfColumn(name)
        val col = newCol(idx)
        schema.insert(idx, col)
        for(i <- idx+1 until schema.size){ schema(i).position = i }
        /* return */ idx
      }
    }
    val col = schema(idx)
    columns(col.id) = col
    executor.addColumn(col.ref)
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

  def buildCache(base: DataFrame)(implicit ec: ExecutionContext): RowCache[Array[Any]] = 
  {
    val annotated = 
      AnnotateWithSequenceNumber(base)
    val df = 
      annotated.select(
        (
          annotated(AnnotateWithSequenceNumber.ATTRIBUTE) +:
          DataFrameOps.columns(base)
        ):_*
      )
    val seqNo = df(AnnotateWithSequenceNumber.ATTRIBUTE)
    val baseCols = DataFrameOps.columnsWhere(df){
                                  _ != AnnotateWithSequenceNumber.ATTRIBUTE
                                }

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

    return cache
  }

  def apply(base: DataFrame)(implicit ec: ExecutionContext) = 
  {
    val cache = buildCache(base)

    val spreadsheet =
      new Spreadsheet(
        new CachedSource(
          base.schema.fields,
          cache,
          Future { base.count() }
        )
      )
    cache.selectForInvalidation = spreadsheet.pickCachePageToDiscard _

    /* return */ spreadsheet
  }

  /**
   * Generate and initialize a simple default spreadsheet over an empty
   * source.
   * 
   * The default spreadsheet will have 3 columns and 5 rows
   */
  def apply()(implicit ec: ExecutionContext): Spreadsheet =
  {
    val spreadsheet = 
      new Spreadsheet(
        new InlineSource(
          schema = Array(),
          data = Array()
        ),
        mutable.ArrayBuffer(
          OutputColumn(
            DefaultValue(null),
            StructField("A", StringType),
            0,
            0
          ),
          OutputColumn(
            DefaultValue(null),
            StructField("B", StringType),
            1,
            1
          ),
          OutputColumn(
            DefaultValue(null),
            StructField("C", StringType),
            2,
            2
          )
        )
      )
    spreadsheet.insertRows(0, 5)
    return spreadsheet
  }

  def bindVariables(expression: Expression, schema: Iterable[OutputColumn]): Expression =
  {
    expression.transform {
      case UnresolvedAttribute(Seq(attr)) => 
        schema.find { _.output.name.toLowerCase == attr.toLowerCase }
              .map { column => 
                RValueExpression(OffsetCell(column.ref, 0))
              }
              .getOrElse {
                InvalidRValue(s"Unknown column: '$attr'")
              }
    }
  }

}