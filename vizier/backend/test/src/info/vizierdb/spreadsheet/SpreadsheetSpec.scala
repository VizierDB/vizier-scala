package info.vizierdb.spreadsheet

import org.specs2.mutable.Specification
import info.vizierdb.test.SharedTestResources
import org.specs2.specification.BeforeAll
import info.vizierdb.Vizier
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.util.Try
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.IntegerType
import play.api.libs.json.JsString
import scala.collection.mutable.Queue

class SpreadsheetSpec
  extends Specification
  with BeforeAll
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def beforeAll(): Unit = SharedTestResources.init()

  class SpreadsheetImplicits(spread: Spreadsheet)
  {
    def forceGetCell(col: Int, row: Long): Option[Try[Any]] =
    {
      val fut = spread.executor.getFuture(spread.schema(col).ref, row)
      Await.ready(fut, Duration(10, TimeUnit.SECONDS))
      spread.getCell(col, row)
    }

    def events: Queue[String] =
    {
      val queue = Queue[String]()
      spread.callbacks.append(new SpreadsheetCallbacks {

        override def refreshEverything(): Unit = 
          queue.enqueue("refresh_full")

        override def refreshHeaders(): Unit =
          queue.enqueue("refresh_headers")

        override def refreshData(): Unit =
          queue.enqueue("refresh_data")

        override def refreshRows(from: Long, count: Long): Unit = 
          queue.enqueue(s"refresh_rows:[$from,${from+count}]")

        override def refreshCell(column: Int, row: Long): Unit = 
          queue.enqueue(s"refresh_cell:$column[$row]")

        override def sizeChanged(newSize: Long): Unit = 
          queue.enqueue(s"refresh_size:$newSize")

      })
      return queue
    }
  }
  implicit def addSpreadsheetImplicits(spread: Spreadsheet): SpreadsheetImplicits =
    new SpreadsheetImplicits(spread)

  "Basic functionality" >>
  {
    val df = Vizier.sparkSession
                   .read
                   .option("header", "true")
                   .schema(StructType(Array(
                    StructField("A", IntegerType),
                    StructField("B", IntegerType),
                    StructField("C", IntegerType),
                   )))
                   .csv("test_data/r.csv")

    val spread: Spreadsheet = Spreadsheet(df)
    val events = spread.events
    spread.subscribe(0, 100)

    spread.forceGetCell(0, 0).get.get must beEqualTo(1)
    events.clear()

    /* Edit a cell */
    spread.editCell(1, 2, JsString("=A+C"))
    spread.forceGetCell(1, 2).get.get must beEqualTo(3)
    while(!events.isEmpty){ 
      // we might get several refresh events
      events.dequeue() must beEqualTo("refresh_cell:1[2]")
    }

    /* Insert a column and a new value */
    spread.insertColumn("D", before = None, dataType = IntegerType)
    spread.columns.mapValues { _.output.name} must contain( 3 -> "D" )
    events.dequeue() must beEqualTo("refresh_full")
    events.isEmpty should beTrue // performance: we don't want multiple refresh events triggered

    spread.editCell(3, 0, JsString("=B+C"))
    spread.forceGetCell(3, 0).get.get must beEqualTo(5)
    events.clear() 

    // println(spread.executor.updates)

    /* Delete and insert rows */
    spread.moveRows(0, 3, 1)
    spread.forceGetCell(0, 3).get.get must beEqualTo(1)
    spread.forceGetCell(1, 3).get.get must beEqualTo(2)
    spread.forceGetCell(2, 3).get.get must beEqualTo(3)
    spread.forceGetCell(3, 3).get.get must beEqualTo(5) // the cell inserted in column 'D' above

    spread.forceGetCell(0, 0).get.get must beEqualTo(1)
    spread.forceGetCell(1, 0).get.get must beEqualTo(3)
    spread.forceGetCell(2, 0).get.get must beEqualTo(1)

    spread.forceGetCell(1, 1).get.get must beEqualTo(3) // the edit to column 'B' above
    spread.executor.getExpression(col = spread.columns(1).ref, row = 1) must not beEmpty

    // println(spread.executor.updates)
    spread.executor.updates(spread.columns(1).ref)._1.get(1) must not beEmpty
    
    spread.executor.updates(spread.columns(3).ref)._1.get(3) must not beEmpty
  }
}