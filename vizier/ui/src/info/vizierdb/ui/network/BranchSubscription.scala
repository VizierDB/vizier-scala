package info.vizierdb.ui.network

import play.api.libs.json._
import org.scalajs.dom
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
import scala.util.{ Success, Failure }
import info.vizierdb.ui.components.Project
import info.vizierdb.ui.Vizier

class BranchSubscription(
  project: Project, 
  branchId: Identifier, 
)
  extends Object
  with Logging
{
  var socket = getSocket() 
  var keepaliveTimer: SetIntervalHandle = null

  val connected = Var(false)
  val awaitingReSync = Var(false)
  val modules = new RxBufferVar[ModuleSubscription]()
  val maxTimeWithoutNotification: Long = 5000L
  var executionStartTime: Long = -1
  val properties = Var[Map[String, JsValue]](Map.empty)
  
  protected[ui] def getSocket(): dom.WebSocket =
  {
    logger.info(s"Connecting to ${Vizier.api.websocket}")
    val s = new dom.WebSocket(Vizier.api.websocket)
    s.onopen = onConnected
    s.onclose = onClosed
    s.onerror = onError
    s.onmessage = onMessage
    keepaliveTimer = setInterval(20000) { keepalive(s) }
    s
  }

  private def keepalive(s: dom.WebSocket)
  {
    Client.ping()
          .onComplete { 
            case Success(ts) => logger.trace(s"Ping response $ts")
            case Failure(error) => logger.error(s"Ping error: $error")
          }
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

  def onSync(workflow: serialized.WorkflowDescription) = 
  {
    logger.debug("Got initial sync")
    modules.clear()
    modules ++= workflow.modules
                        .zipWithIndex
                        .map { case (initialState, idx) =>
                          new ModuleSubscription(initialState, Left(this), idx) 
                        }
    awaitingReSync() = false
  }
  def onConnected(event: dom.Event)
  {
    connected() = true
    logger.debug("Connected!")
    awaitingReSync() = true
    
    Client.subscribe(project.projectId, branchId)
          .onSuccess { case workflow => onSync(workflow) } 
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
  def onMessage(message: dom.MessageEvent) =
  {
    logger.trace(s"Got: ${message.data.asInstanceOf[String].take(20)}")
    Json.parse(message.data.asInstanceOf[String])
        .as[websocket.WebsocketResponse] match {

          /*************************************************************/

          case websocket.NormalWebsocketResponse(id, message) => 
            reportSuccess(id, message)

          /*************************************************************/

          case websocket.ErrorWebsocketResponse(id, error, detail) =>
            reportError(id, error, detail)

          /*************************************************************/

          case websocket.NotificationWebsocketMessage(event) =>
            event match {

              /////////////////////////////////////////////////

              case delta.InsertCell(cell, position) =>
                modules.insert(
                  position,
                  new ModuleSubscription(cell, Left(this), position)
                )
                modules.drop(position)
                  .zipWithIndex
                  .foreach { case (module, offset) => module.position = position+offset }

              /////////////////////////////////////////////////

              case delta.UpdateCell(cell, position) => 
                modules.update(
                  position,
                  new ModuleSubscription(cell, Left(this), position)
                )
             
              /////////////////////////////////////////////////

              case delta.DeleteCell(position) =>
                modules.remove(position)
                modules.drop(position-1)
                       .zipWithIndex
                       .foreach { case (module, offset) => module.position = position+offset }
             
              /////////////////////////////////////////////////

              case delta.UpdateCellState(position, state, timestamps) =>
                logger.debug(s"State Update: ${state} @ ${position}")
                if(state == ExecutionState.RUNNING){
                  executionStartTime =  java.lang.System.currentTimeMillis()
                } else if(state == ExecutionState.DONE && executionStartTime != -1) {
                  val executionEndTime = java.lang.System.currentTimeMillis()
                  val timeToExecute = executionEndTime - executionStartTime
                  logger.debug(s"Cell @ ${position} executed in ${timeToExecute / 1000.0} seconds")
                  if(timeToExecute > maxTimeWithoutNotification && dom.experimental.Notification.permission == "granted") {
                    val notificationBody: js.UndefOr[String] = s"${(modules(position).toc).get.title} DONE"
                    val notification = new dom.experimental.Notification("VizierDB", dom.experimental.NotificationOptions(notificationBody))
                  }
                  executionStartTime = -1
                } else {
                  executionStartTime = -1
                }
                val module = modules(position)
                module.state() = state
                module.timestamps() = timestamps
             
              /////////////////////////////////////////////////
              
              case delta.UpdateCellArguments(position, arguments, moduleId) =>
                logger.debug(s"Arguments Update: $arguments @ $position")
                val module = modules(position)
                module.arguments = arguments
                module.id = moduleId


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

              /////////////////////////////////////////////////

              case delta.UpdateBranchProperties(newProperties) => 
                properties() = newProperties

              /////////////////////////////////////////////////

              case delta.UpdateProjectProperties(newProperties) => 
                project.properties() = newProperties
                
            }
        }
  }

  val BASE_PATH = Seq("info", "vizierdb", "api", "websocket", "BranchWatcherAPI")
  var nextMessageId = 0l;
  val messageCallbacks = mutable.Map[Long, Promise[JsValue]]()

  def reportSuccess(id: Long, message: JsValue) =
    messageCallbacks.remove(id) match {
      case Some(promise) => promise.success(message)
      case None => logger.warn(s"Response to unsent messageId: ${id}")
    }

  def reportError(id: Long, error: String, detail: Option[String] = None) =
    messageCallbacks.remove(id) match {
      case Some(promise) => logger.warn(s"Error Response: $error"); 
                            promise.failure(new Exception(error))
      case None => logger.warn(s"ERROR Response to unsent messageId: ${id}")
    }

  def logRequest: (Long, Future[JsValue]) =
  {
    val id = nextMessageId; 
    nextMessageId += 1
    val response = Promise[JsValue]()
    messageCallbacks.put(id, response) 
    return (id, response.future)
  }

  def makeRequest(leafPath: Seq[String], args: Map[String, JsValue]): Future[JsValue] =
  {
    val (id, response) = logRequest
    val request = websocket.WebsocketRequest(id, BASE_PATH++leafPath, args)
    logger.trace(s"${request.path.mkString("/")} <- Request ${request.id}")
    socket.send(Json.toJson(request).toString)
    return response
  }


  object Client extends BranchWatcherAPIProxy
  {
    def sendRequest(leafPath: Seq[String], args: Map[String, JsValue]): Future[JsValue] =
      makeRequest(leafPath, args)

    def ping(): Future[Long] =
      sendRequest(Seq("ping"), Map.empty).map { _.as[Long] }

    def subscribe(projectId: Identifier, branchId: Identifier): Future[serialized.WorkflowDescription] =
      sendRequest(Seq("subscribe"), Map("projectId" -> Json.toJson(projectId), "branchId" -> Json.toJson(branchId))).map { _.as[serialized.WorkflowDescription] }
  }
}