package info.vizierdb.ui.network

import rx._
import info.vizierdb.types._
import scala.scalajs.js.timers._
import org.scalajs.dom
import info.vizierdb.api.spreadsheet._
import play.api.libs.json._
import info.vizierdb.util.Logging
import info.vizierdb.serialized.DatasetColumn
import info.vizierdb.ui.components.dataset.TableDataSource
import info.vizierdb.util.RowCache
import info.vizierdb.serialized.DatasetRow
import scala.collection.mutable

class SpreadsheetClient(projectId: Identifier, datasetId: Identifier, api: API)
  extends TableDataSource
  with Logging
{
  var socket = getSocket() 
  var keepaliveTimer: SetIntervalHandle = null

  val connected = Var(false)
  val awaitingReSync = Var(false)
  var schema = Seq[DatasetColumn]()
  var size = 0l
  val rows = mutable.Map[Long, RowBatch]()

  val BUFFER_SIZE = 20
  var BUFFER_PAGE = 1000

  protected[ui] def getSocket(): dom.WebSocket =
  {
    logger.info(s"Connecting to ${api.spreadsheet}")
    val s = new dom.WebSocket(api.spreadsheet)
    s.onopen = onConnected
    s.onclose = onClosed
    s.onerror = onError
    s.onmessage = onMessage
    keepaliveTimer = setInterval(20000) { keepalive(s) }
    s
  }

  def send(message: SpreadsheetRequest)
  {
    socket.send(Json.toJson(message).toString)
  }

  def onMessage(message: dom.MessageEvent) =
  {
    logger.trace(s"Got: ${message.data.asInstanceOf[String].take(20)}")
    Json.parse(message.data.asInstanceOf[String])
        .as[SpreadsheetResponse] match {
          case UpdateSchema(newSchema)         => setSchema(newSchema)
          case UpdateSize(newSize)             => setSize(newSize)
          case Pong(id)                        => logger.debug(s"Pong $id")
          case DeliverRows(start, data)        => updateData(start, data)
          case DeliverCell(column, row, value) => updateData(column, row, value)
          case ReportError(err, detail)        => logger.error(err)
        }
  }

  def onConnected(event: dom.Event)
  {
    connected() = true
    logger.debug("Connected!")
    awaitingReSync() = true
    send(OpenSpreadsheet(projectId, datasetId))
  }

  def onClosed(event: dom.Event)
  {
    if(keepaliveTimer != null) { 
      clearInterval(keepaliveTimer)
      keepaliveTimer = null
    }
    connected() = false
  }
  def onError(event: dom.Event) = 
  {
    logger.error(s"Error: $event")
  }

  private def keepalive(s: dom.WebSocket)
  {
    send(Ping(0))
  }

  def subscribe(start: Long) =
  {
    send(SubscribeRows(start, BUFFER_PAGE))
  }

  def unsubscribe()

  def prefill(source: RowCache[DatasetRow]): Unit = 
  {
    // There's a bunch of messy state involving subscriptions, page sizes, etc...
    // I'm not going to deal with it right now.  If you hit the following assertion
    // then it's not impossible, but will require a bit of thought.
    assert(rows.isEmpty, "Can't prefill a spreadsheet client once it is in-use")
    BUFFER_PAGE = source.BUFFER_PAGE
    for((start, batch) <- source.cache){
      batch.data match {
        case None       => rows.put(start, new RowBatch(start)) // still waiting on data,  set up a placeholder
        case Some(data) => rows.put(start, new RowBatch(start, data))
      }
      subscrbe(start)
    }
  }

  def setSchema(newSchema: Seq[DatasetColumn]): Unit =
    schema = newSchema

  def setSize(newSize: Long): Unit =
    size = newSize

  def updateData(start: Long, data: Array[Array[SpreadsheetCell]]): Unit =
  {

  }
  def updateData(column: Int, start: Long, data: SpreadsheetCell): Unit =
  {

  }

  class RowBatch(val start: Long, val data: Array[Array[SpreadsheetCell]])
  {
    def this(start: Long) = ???
    def this(start: Long, data: Seq[DatasetColumn])
  }

}