package info.vizierdb.spreadsheet

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

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import scala.concurrent.duration._
import org.apache.spark.sql.DataFrame
import info.vizierdb.Vizier
import org.apache.spark.sql.types._
import scala.concurrent.ExecutionContext
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadFactory
import java.util.concurrent.BlockingQueue
import scala.collection.mutable
import info.vizierdb.util.TimerUtils
import org.apache.spark.sql.catalyst.expressions._

class SpreadsheetBenchmark
  extends Specification
  with BeforeAll
{
  val executor = 
    new ThreadPoolExecutor(
      /* corePoolSize    = */ 8,
      /* maximumPoolSize = */ 8,
      /* keepAliveTime   = */ 1l,
      /* unit            = */ TimeUnit.MINUTES,
      /* workQueue       = */ new LinkedBlockingQueue[Runnable](): BlockingQueue[Runnable],
      /* threadFactory   = */ new ThreadFactory { def newThread(r: Runnable) = new Thread(r); }
    )

  implicit val ec = ExecutionContext.fromExecutorService(executor)

  val TIMEOUT = 2.seconds

  def beforeAll(): Unit = SharedTestResources.init()

  def lineitem(): DataFrame =
  {
    Vizier.sparkSession
          .read
          .option("delimiter", "|")
          .schema(StructType(Array(
            StructField("OrderKey", IntegerType),
            StructField("SuppKey", IntegerType),
            StructField("PartKey", IntegerType),
            StructField("LineNumber", IntegerType),
            StructField("Quantity", IntegerType),
            StructField("ExtendedPrice", FloatType),
            StructField("Discount", FloatType),
            StructField("Tax", FloatType),
            StructField("ReturnFlag", StringType),
            StructField("LineStatus", StringType),
            StructField("ShipDate", DateType),
            StructField("CommitDate", DateType),
            StructField("ReceiptDate", DateType),
            StructField("ShipInstruct", StringType),
            StructField("ShipMode", StringType),
            StructField("Comment", StringType),
          )))
          .csv("test_data/lineitem.tbl")
  }

  "Time Results" >> 
  {
    val START = 5500


    val client =
      TimerUtils.logTime("Init Spreadsheet"){
        val client = new MockClient()
        client.init(START)
        client.waitForQuiescence
        client
      }

    val rowCount = client.spreadsheet.size
    val ext_price = client.spreadsheet.schema.find { _.output.name == "ExtendedPrice" }.get.ref
    val discount = client.spreadsheet.schema.find { _.output.name == "Discount" }.get.ref
    val tax = client.spreadsheet.schema.find { _.output.name == "Tax" }.get.ref

    TimerUtils.logTime("Monitoring Overhead"){
      client.watchAllVisible()
      client.waitForQuiescence()
    }

    val charge = 
      TimerUtils.logTime("Init Formulas"){
        val base_price = client.spreadsheet.insertColumn("base_price", None, FloatType)
        val disc_price = client.spreadsheet.insertColumn("disc_price", None, FloatType)
        val charge = client.spreadsheet.insertColumn("charge", None, FloatType)
        val sum_charge = client.spreadsheet.insertColumn("sum_charge", None, FloatType)
        client.spreadsheet.overlay.update(
          base_price(0, rowCount),
          Cast(ext_price.offsetBy(0).expr, FloatType)
        )
        client.spreadsheet.overlay.update(
          disc_price(0, rowCount),
          Multiply(
            base_price.offsetBy(0).expr,
            Subtract(
              Literal(1.0f),
              discount.offsetBy(0).expr
            )
          )
        )
        client.spreadsheet.overlay.update(
          charge(0, rowCount),
          Multiply(
            base_price.offsetBy(0).expr,
            Add(
              Literal(1.0f),
              tax.offsetBy(0).expr
            )
          )
        )
        client.spreadsheet.overlay.update(
          sum_charge(0),
          Literal(0.0f)
        )
        client.spreadsheet.overlay.update(
          sum_charge(1, rowCount),
          Add(
            sum_charge.offsetBy(-1).expr,
            charge.offsetBy(0).expr,
          )
        )
        client.watchAllVisible()
        client.waitForQuiescence()
        charge
      }

    TimerUtils.logTime("Update one"){
      client.spreadsheet.overlay.update(
        ext_price(0),
        Literal(1000.0f)
      )
      client.watchAllVisible()
      client.waitForQuiescence()
    }

    TimerUtils.logTime("Update all"){
      client.spreadsheet.overlay.update(
        ext_price(0, rowCount),
        Literal(2000.0f)
      )
      client.watchAllVisible()
      client.waitForQuiescence()
    }

    // for(i <- 0 until 10)
    // {
    //   println(s"Row: $i: ${client.spreadsheet.getRow(i).mkString("|")}")
    // }

    ok
  }

  class MockClient extends SpreadsheetCallbacks
  {
    val data = lineitem().limit(6000)
    val pendingRows = mutable.Set[Long]()
    val spreadsheet = Spreadsheet(data)

    var visible = 0l until 0l

    def init(position: Long, viewable: Int = 50) 
    {
      spreadsheet.callbacks += this
      spreadsheet.subscribe(position, viewable);

      visible = position until (position + viewable)

      watchAllVisible()
    }

    def watchAllVisible()
    {
      synchronized {
        pendingRows ++= visible.toSeq
      }
      checkPending(visible)
    }

    def waitForQuiescence()
    {
      synchronized {
        while( !pendingRows.isEmpty ){ 
          this.wait(10000)
        }
      }
    }

    def checkPending(rows: Seq[Long] = visible): Boolean =
    {
      synchronized {
        for(row <- rows)
        {
          if(spreadsheet.getRow(row).forall { elem => elem.isDefined && elem.get.isSuccess })
          {
            pendingRows.remove(row)
          }
        }
        // println(s"Still pending: ${pendingCells.mkString(", ")}")
        if(pendingRows.isEmpty){
          this.notify()
        }
      }
      return pendingRows.isEmpty
    }

    override def refreshEverything(): Unit = 
    {
      checkPending()
    }

    override def refreshHeaders(): Unit =
    {
      /* ignore */
    }

    override def refreshData(): Unit = 
    {
      checkPending()
    }

    override def refreshRows(from: Long, count: Long): Unit = 
    {
      checkPending(from until (from + count))
    }

    override def refreshCell(column: Int, row: Long): Unit =
    {
      checkPending(Seq(row))
    }

    override def sizeChanged(newSize: Long): Unit = 
    {
      checkPending()
    }


  }

}