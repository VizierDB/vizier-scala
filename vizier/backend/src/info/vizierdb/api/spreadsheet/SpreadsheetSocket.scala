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
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.{
  OnWebSocketClose,
  OnWebSocketConnect,
  OnWebSocketMessage,
  WebSocket,
}
import org.eclipse.jetty.websocket.servlet.{
  WebSocketCreator, 
  ServletUpgradeRequest, 
  ServletUpgradeResponse,
  WebSocketServlet,
  WebSocketServletFactory
}
import org.eclipse.jetty.websocket.server.WebSocketServerFactory
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

@WebSocket
class SpreadsheetSocket
  extends SpreadsheetCallbacks
  with LazyLogging
{

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private var session: Session = null
  lazy val client = session.getRemoteAddress.toString

  var spreadsheet: Spreadsheet = null

  @OnWebSocketConnect
  def onOpen(session: Session) 
  { 
    logger.debug(s"Websocket opened: $session")
    this.session = session 
  }
  @OnWebSocketClose
  def onClose(closeCode: Int, message: String) 
  {
    logger.debug(s"Websocket closed with code $closeCode: $message")
  }

  @OnWebSocketMessage
  def onText(data: String): Unit =
  {
    logger.trace(s"Websocket received ${data.length()} bytes: ${data.take(20)}")    

    try {
      Json.parse(data).as[SpreadsheetRequest] match {
        case OpenSpreadsheet(projectId, datasetId) => 
        {
          if(spreadsheet != null){ send(ReportError("Spreadsheet already opened", "")); return }
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
            case Failure(err) => send(ReportError(err))
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
      session.getRemote.sendString(Json.stringify(Json.toJson(message)))
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
  object Creator extends WebSocketCreator
  {
    override def createWebSocket(
      request: ServletUpgradeRequest, 
      response: ServletUpgradeResponse
    ): Object = 
    {
      new SpreadsheetSocket()
    }
  }

  object Servlet extends WebSocketServlet {
    def configure(factory: WebSocketServletFactory)
    {
      factory.getPolicy().setIdleTimeout(100000)
      factory.setCreator(Creator)
    }

  }
}