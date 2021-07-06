package info.vizierdb.ui.network

import play.api.libs.json._
import org.scalajs.dom
import info.vizierdb.ui.API
import rx._
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.collection.mutable
import scala.concurrent.{ Promise, Future }
import info.vizierdb.ui.rxExtras.RxBufferVar
import info.vizierdb.types._
import scala.scalajs.js.timers._
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.util.Logging
import info.vizierdb.serialized
import info.vizierdb.api.websocket
import info.vizierdb.delta
import info.vizierdb.serializers._
import autowire._

class BranchSubscription(branchId: Identifier, projectId: Identifier, api: API)
  extends Object
  with Logging
{
  var socket = getSocket() 
  var keepaliveTimer: SetIntervalHandle = null

  val connected = Var(false)
  val awaitingReSync = Var(false)
  val modules = new RxBufferVar[ModuleSubscription]()

  protected[ui] def getSocket(): dom.WebSocket =
  {
    logger.info(s"Connecting to ${api.urls.websocket}")
    val s = new dom.WebSocket(api.urls.websocket)
    s.onopen = onConnected
    s.onclose = onClosed
    s.onerror = onError
    s.onmessage = onMessage
    keepaliveTimer = setInterval(20000) { keepalive(s) }
    s
  }

  private def keepalive(s: dom.WebSocket)
  {
    s.send(
      JSON.stringify(
        js.Dictionary(
          "operation" -> "ping",
        )
      )
    )
  }

  /**
   * Close the websocket
   */
  def close()
  {
    socket.close()
    if(keepaliveTimer != null) { 
      clearInterval(keepaliveTimer)
      keepaliveTimer = null
    }
    connected() = false
  }

  def onConnected(event: dom.Event)
  {
    connected() = true
    logger.debug("Connected!")
    awaitingReSync() = true

    Client[websocket.BranchWatcherAPI]
      .subscribe(projectId, branchId)
      .call()
      .onSuccess { case workflow => 
        logger.debug("Got initial sync")
        modules.clear()
        modules ++= workflow.modules
                            .zipWithIndex
                            .map { case (initialState, idx) =>
                              new ModuleSubscription(initialState, this, idx) 
                            }
        awaitingReSync() = false
      }
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

  implicit val websocketRequestFormat = Json.format[websocket.WebsocketRequest]
  implicit val normalwebsocketresponseformat = Json.format[websocket.NormalWebsocketResponse]
  implicit val errorwebsocketresponseformat = Json.format[websocket.ErrorWebsocketResponse]
  implicit val notificationwebsocketmessageformat = Json.format[websocket.NotificationWebsocketMessage]
  implicit val websocketResponseFormat = Json.format[websocket.WebsocketResponse]

  var nextMessageId = 0l;
  val messageCallbacks = mutable.Map[Long, Promise[JsValue]]()

  def onMessage(message: dom.MessageEvent) =
  {
    logger.trace(s"Got: ${message.data}")
    Json.parse(message.data.asInstanceOf[String])
        .as[websocket.WebsocketResponse] match {

          /*************************************************************/

          case websocket.NormalWebsocketResponse(id, message) => 
            messageCallbacks.remove(id) match {
              case Some(promise) => promise.success(message)
              case None => logger.warn(s"Response to unsent messageId: ${id}")
            }

          /*************************************************************/

          case websocket.ErrorWebsocketResponse(id, error, detail) =>
            messageCallbacks.remove(id) match {
              case Some(promise) => promise.failure(new Exception(error))
              case None => logger.warn(s"ERROR Response to unsent messageId: ${id}")
            }

          /*************************************************************/

          case websocket.NotificationWebsocketMessage(event) =>
            event match {

              /////////////////////////////////////////////////

              case delta.InsertCell(cell, position) =>
                modules.insert(
                  position,
                  new ModuleSubscription(cell, this, position)
                )
                modules.drop(position)
                  .zipWithIndex
                  .foreach { case (module, offset) => module.position = position+offset }

              /////////////////////////////////////////////////

              case delta.UpdateCell(cell, position) => 
                modules.update(
                  position,
                  new ModuleSubscription(cell, this, position)
                )
             
              /////////////////////////////////////////////////

              case delta.DeleteCell(position) =>
                modules.remove(position)
                modules.drop(position-1)
                       .zipWithIndex
                       .foreach { case (module, offset) => module.position = position+offset }
             
              /////////////////////////////////////////////////

              case delta.UpdateCellState(position, state) =>
                logger.debug(s"State Update: ${state} @ ${position}")
                modules(position).state() = state
             
              /////////////////////////////////////////////////

              case delta.AppendCellMessage(position, stream, message) =>
                logger.debug(s"New Message @ $position")
                modules(position).messages += message.withStream(stream)

             
              /////////////////////////////////////////////////

              case delta.AdvanceResultId(position, resultId) =>
                logger.debug("Reset Result")
                val module = modules(position)
                module.messages.clear()
                module.outputs() = Map[String,Option[serialized.ArtifactSummary]]()

             
              /////////////////////////////////////////////////

              case delta.UpdateCellOutputs(position, outputs) =>
                val module = modules(position)
                logger.debug(s"Adding outputs: ${outputs} -> ${module.outputs}")
                module.outputs() = 
                  outputs.map { 
                    case delta.DeltaOutputArtifact(Left(deleted)) => 
                      deleted -> None
                    case delta.DeltaOutputArtifact(Right(artifact)) => 
                      artifact.name -> Some(artifact)
                  }
                  .toMap
            }
        }
  }

  object Client extends autowire.Client[JsValue, Reads, Writes]
  {
    def write[T: Writes](t: T) = Json.toJson(t)
    def read[T: Reads](s: JsValue): T = Json.fromJson[T](s).get
    
    override def doCall(req: Request): Future[JsValue] =
    {
      val id = nextMessageId
      val response = Promise[JsValue]
      nextMessageId += 1;
      logger.trace(s"${req.path.mkString("/")} <- Request ${id}")
      messageCallbacks.put(id, response)
      val wrappedRequest = websocket.WebsocketRequest(id, req)
      val requestJson = Json.toJson(wrappedRequest)
      socket.send(requestJson.toString)
      return response.future
    }

  }
}