/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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
import play.api.libs.json.Json
import info.vizierdb.catalog.Artifact
import info.vizierdb.types._
import org.apache.spark.sql.types.StringType

class EncodedSpreadsheetSpec
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

  "Standard Co-Dec" >>
  {
    // This test mirrors SpreadsheetSpec's basic test

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

    /* Edit a cell */
    spread.editCell(1, 2, JsString("=A+C"))

    /* Insert a column and a new value */
    spread.insertColumn("D", before = None, dataType = IntegerType)
    spread.editCell(column =  3, row = 0, JsString("=B+C"))

    /* Delete and insert rows */
    spread.moveRows(0, 3, 1)

    val encoded = EncodedSpreadsheet.fromSpreadsheet(spread)

    {
      val decoded = encoded.rebuildFromDataframe(df)
      decoded.subscribe(0, 100)

      decoded.forceGetCell(0, 3).get.get must beEqualTo(1)
      decoded.forceGetCell(1, 3).get.get must beEqualTo(2)
      decoded.forceGetCell(2, 3).get.get must beEqualTo(3)

      decoded.getExpression(column = 3, row = 3) must not beEmpty

      decoded.forceGetCell(3, 3).get.get must beEqualTo(5) // the cell inserted in column 'D' above

      decoded.forceGetCell(0, 0).get.get must beEqualTo(1)
      decoded.forceGetCell(1, 0).get.get must beEqualTo(3)
      decoded.forceGetCell(2, 0).get.get must beEqualTo(1)

      decoded.forceGetCell(1, 1).get.get must beEqualTo(3) // the edit to column 'B' above
    }

    val json = Json.toJson(encoded)

    {
      val decoded = json.as[EncodedSpreadsheet].rebuildFromDataframe(df)
      decoded.subscribe(0, 100)

      decoded.forceGetCell(0, 3).get.get must beEqualTo(1)
      decoded.forceGetCell(1, 3).get.get must beEqualTo(2)
      decoded.forceGetCell(2, 3).get.get must beEqualTo(3)

      decoded.getExpression(column = 3, row = 3) must not beEmpty

      decoded.forceGetCell(3, 3).get.get must beEqualTo(5) // the cell inserted in column 'D' above

      decoded.forceGetCell(0, 0).get.get must beEqualTo(1)
      decoded.forceGetCell(1, 0).get.get must beEqualTo(3)
      decoded.forceGetCell(2, 0).get.get must beEqualTo(1)

      decoded.forceGetCell(1, 1).get.get must beEqualTo(3) // the edit to column 'B' above      
    }

    val dfConstructor = new SpreadsheetDatasetConstructor(Some(1), encoded)

    {
      dfConstructor.dependencies must beEqualTo(Set(1))

      val decoded = dfConstructor.construct( _ => 
        new Artifact(1, 1, ArtifactType.DATASET, null, "vizier/dataset", Array.empty) {
          override def dataframeFromContext(ctx: Identifier => Artifact) = df 
        }
      )

      // decoded.explain(true)
      // decoded.show()

      val data = decoded.collect()
                        .map { row => 
                          Array(
                            row.getAs[Int]("A"),
                            row.getAs[Int]("B"),
                            row.getAs[Int]("C"),
                            row.getAs[Int]("D"),
                          )
                        }

      data must haveSize(spread.size.toInt)

      data(3)(0) must beEqualTo(1)
      data(3)(1) must beEqualTo(2)
      data(3)(2) must beEqualTo(3)
      data(3)(3) must beEqualTo(5) // the cell inserted in column 'D' above
      data(0)(0) must beEqualTo(1)
      data(0)(1) must beEqualTo(3)
      data(0)(2) must beEqualTo(1)
      data(1)(1) must beEqualTo(3) // the edit to column 'B' above      
    }
  }

  "Serializing Multiple Inserts" >> 
  {
    val df = Vizier.sparkSession
                   .read
                   .option("header", "true")
                   .schema(StructType(Array(
                    StructField("A", StringType),
                    StructField("B", StringType),
                    StructField("C", StringType),
                   )))
                   .csv("test_data/r.csv")
    val spread: Spreadsheet = Spreadsheet(df)
    spread.subscribe(0, 100)

    spread.editCell(2, 0, JsString("shazbot"))
    spread.editCell(1, 2, JsString("fribble"))

    spread.forceGetCell(2, 0).get.get.toString must beEqualTo("shazbot")
    spread.forceGetCell(1, 2).get.get.toString must beEqualTo("fribble")

    val encoded = Json.toJson(EncodedSpreadsheet.fromSpreadsheet(spread))

    val rehydrated:Spreadsheet = encoded.as[EncodedSpreadsheet].rebindVariables.rebuildFromDataframe(df)
    rehydrated.subscribe(0, 100)

    rehydrated.forceGetCell(2, 0).get.get.toString must beEqualTo("shazbot")
    rehydrated.forceGetCell(1, 2).get.get.toString must beEqualTo("fribble")

    rehydrated.editCell(1, 0, JsString("murgurgle"))
    rehydrated.editCell(0, 2, JsString("splat"))
    rehydrated.forceGetCell(1, 0).get.get.toString must beEqualTo("murgurgle")
    rehydrated.forceGetCell(0, 2).get.get.toString must beEqualTo("splat")

    val reencoded = EncodedSpreadsheet.fromSpreadsheet(rehydrated)
    val rerehydrated:Spreadsheet = reencoded.rebindVariables.rebuildFromDataframe(df)

    rerehydrated.subscribe(0, 100)
    rerehydrated.forceGetCell(2, 0).get.get.toString must beEqualTo("shazbot")
    rerehydrated.forceGetCell(1, 2).get.get.toString must beEqualTo("fribble")
    rerehydrated.forceGetCell(1, 0).get.get.toString must beEqualTo("murgurgle")
    rerehydrated.forceGetCell(0, 2).get.get.toString must beEqualTo("splat")

  }

}