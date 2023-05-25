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
  }
  implicit def addSpreadsheetImplicits(spread: Spreadsheet): SpreadsheetImplicits =
    new SpreadsheetImplicits(spread)

  "Basic test" >>
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
    spread.subscribe(0, 100)

    spread.forceGetCell(0, 0).get.get must beEqualTo(1)
    spread.editCell(1, 2, JsString("=A+C"))
    spread.forceGetCell(1, 2).get.get must beEqualTo(3)
  }
}