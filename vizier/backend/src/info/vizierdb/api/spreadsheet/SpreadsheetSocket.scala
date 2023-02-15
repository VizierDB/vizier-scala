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
package info.vizierdb.api.spreadsheet

import scalikejdbc._
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._
import info.vizierdb.spreadsheet.Spreadsheet
import info.vizierdb.catalog.Artifact
import info.vizierdb.types._
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import info.vizierdb.spreadsheet.SpreadsheetCallbacks
import info.vizierdb.serialized.DatasetColumn
import org.apache.spark.util.collection.ErrorMessage
import info.vizierdb.catalog.CatalogDB
import scala.concurrent.ExecutionContext
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import org.reactivestreams.Publisher

class SpreadsheetSocket(client: String)(implicit val ec: ExecutionContext, system: ActorSystem, mat: Materializer)
  extends SpreadsheetCallbacks
  with LazyLogging
{
  var spreadsheet: Spreadsheet = null
  var projectId: Identifier = 0l

  logger.debug(s"[$client] Websocket opened")
  
  val (remote, publisher): (ActorRef, Publisher[TextMessage.Strict]) =
        Source.actorRef[String](16, OverflowStrategy.fail)
          .map(msg => TextMessage.Strict(msg))
          .toMat(Sink.asPublisher(false))(Keep.both)
          .run()
    
  val sink: Sink[Message, Any] = Flow[Message]
      .map {
        case TextMessage.Strict(msg) =>
          onText(msg)
        case _ => 
          logger.error(s"[$client] Unexpected websocket message")
      }
      .to(Sink.onComplete { msg => {
        logger.debug(s"[$client] Websocket closed: $msg")
      }})

  val flow = 
    Flow.fromSinkAndSource(sink, Source.fromPublisher(publisher))

  def onText(data: String): Unit =
  {
    logger.trace(s"[$client] Websocket received ${data.length()} bytes: ${data.take(20)}")

    try {
      Json.parse(data).as[SpreadsheetRequest] match {
        case OpenSpreadsheet(projectId, datasetId) => 
        {
          if(spreadsheet != null){ send(ReportError("Spreadsheet already opened", "")); return }
          this.projectId = projectId
          logger.trace("Opening spreadsheet")
          Future {
            logger.trace("Initializing spreadsheet!")
            spreadsheet = Spreadsheet(
              CatalogDB.withDB { implicit s => 
                Artifact.get(target = datasetId, projectId = Some(projectId))
                  .dataframe
              }()
            )
            spreadsheet.callbacks += this
            logger.trace("Spreadsheet initialized!")
          }.onComplete {
            case Success(_) => send(Connected("Test Dataset")); refreshEverything()
            case Failure(err) => 
              err.printStackTrace()
              send(ReportError(err))
          }
        }
        case SubscribeRows(row, count) => 
        {
          spreadsheet.subscribe(row, count)
          refreshRows(row, count)
        }
        case UnsubscribeRows(row, count) => 
        {
          spreadsheet.unsubscribe(row, count)
        }
        case EditCell(column, row, v) => 
        {
          spreadsheet.editCell(column, row, v)
        }
        case SaveSpreadsheet(branchId, moduleId, true) =>
        {
          send(SaveSuccessful(spreadsheet.saveAs(projectId, branchId, moduleId)))
        }
        case SaveSpreadsheet(branchId, moduleId, false) =>
        {
          send(SaveSuccessful(spreadsheet.saveAfter(projectId, branchId, moduleId)))
        }
        case Ping(id) => send(Pong(id))
      }
    } catch {
      case e: Throwable => 
        e.printStackTrace()
        send(ReportError(e))
    }
  }

  /**
   * Send a message
   */
  def send(message: SpreadsheetResponse) =
  {
    synchronized {
      logger.trace(s"SEND: ${message.toString.take(200)}")
      remote ! Json.stringify(Json.toJson(message))
    } 
  }
  
  def refreshEverything(): Unit = 
  {
    logger.trace("Refreshing Everything")
    sizeChanged(spreadsheet.size)
    refreshHeaders()
    refreshData()
  }
  
  def refreshHeaders(): Unit =   
    send(UpdateSchema(spreadsheet.schema.map { case output =>
      DatasetColumn(output.id, output.output.name, output.output.dataType)
    }))
  
  def refreshData(): Unit = 
  {
    val t = spreadsheet.schema.map { _.output.dataType }
    val sizeBound = spreadsheet.size
    for( (low, high) <- spreadsheet.subscriptions ){
      if(low < sizeBound){
        val boundHigh = math.min(sizeBound-1, high)
        logger.trace(s"Refreshing [$low,$boundHigh]")
        send(DeliverRows(low, 
          (low to boundHigh).map { rowIndex => 
            spreadsheet.getRow(rowIndex)
                       .zip(t)
                       .map { case (v, t) => SpreadsheetCell(v, t) }
                       .toArray[SpreadsheetCell]
          }.toArray
        ))
      }
    }
  }  

  def refreshRows(from: Long, count: Long): Unit = 
  {
    val t = spreadsheet.schema.map { _.output.dataType }
    val cappedUntil = math.min(spreadsheet.size, from+count)
    logger.trace(s"Refreshing $from+$count -> [$from,$cappedUntil]")
    send(DeliverRows(from, 
      (from until cappedUntil).map { rowIndex => 
        spreadsheet.getRow(rowIndex)
                   .zip(t)
                   .map { case (v, t) => SpreadsheetCell(v, t) }
                   .toArray[SpreadsheetCell]
      }.toArray
    ))
  }
  def refreshCell(column: Int, row: Long): Unit = 
  {
    logger.trace(s"Cell invalidated: $column:$row")
    // These can come in bursts, so make notification delivery asynchronous
    Future {
      try {
        logger.trace(s"Constructing invalidation message for $column:$row")
        val message = DeliverCell(column, row, SpreadsheetCell(spreadsheet.getCell(column, row), spreadsheet.schema(column).output.dataType))
        logger.trace(s"Sending invalidation message $message")
        send(message)
        logger.trace(s"sent invalidation message")
      } catch {
        case t: Throwable => 
          logger.error(s"Error refreshing cell $column:$row: ${t.getMessage()}")
          t.printStackTrace()
      }
    }
  }

  def sizeChanged(newSize: Long): Unit =
  {
    send(UpdateSize(newSize))
  }

}

object SpreadsheetSocket
{
  def monitor(client: String)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer): Flow[Message, Message, Any] =
    new SpreadsheetSocket(client).flow
}