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
package info.vizierdb.api.websocket

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
import autowire._
import scalikejdbc.DB
import scala.collection.mutable
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog._
import info.vizierdb.delta.{ DeltaBus, WorkflowDelta }
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.jetty.websocket.servlet.WebSocketServlet
import info.vizierdb.serializers._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Success, Failure }

// https://git.eclipse.org/c/jetty/org.eclipse.jetty.project.git/tree/jetty-websocket/websocket-server/src/test/java/org/eclipse/jetty/websocket/server/examples/echo/ExampleEchoServer.java

case class SubscribeRequest(
  projectId: String,
  branchId: String
)

object SubscribeRequest {
  implicit val format: Format[SubscribeRequest] = Json.format
}

@WebSocket
class BranchWatcherSocket
  extends LazyLogging
{
  logger.trace("Websocket allocated")
  private var session: Session = null
  var subscription: DeltaBus.Subscription = null
  lazy val client = session.getRemoteAddress.toString

  def registerSubscription(projectId: Identifier, branchId: Identifier): Unit = 
  {
    if(subscription != null){
      logger.warn(s"Websocket ($client) overriding existing subscription")
      DeltaBus.unsubscribe(subscription)
    }
    
    val branch = DB.readOnly { implicit s => Branch.get(projectId, branchId) } 
    
    subscription = DeltaBus.subscribe(branchId, this.notify, s"Websocket $client")
  }

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
    if(subscription != null){
      DeltaBus.unsubscribe(subscription)
    }
  }

  implicit val requestFormat = Json.format[WebsocketRequest]
  implicit val normalResponseWrites = Json.writes[NormalWebsocketResponse]
  implicit val errorResponseWrites = Json.writes[ErrorWebsocketResponse]
  implicit val notificationResponseWrites = Json.writes[NotificationWebsocketMessage]
  implicit val responseWrites = Json.writes[WebsocketResponse]

  @OnWebSocketMessage
  def onText(data: String)
  {
    logger.trace(s"Websocket received ${data.length()} bytes: ${data.take(20)}")
    val request = Json.parse(data).as[WebsocketRequest]
    Router.routes
          .apply(request.autowireRequest)
          .onComplete {
            case Success(result) => send(NormalWebsocketResponse(request.id, result))
            case Failure(error) => send(ErrorWebsocketResponse(request.id, error.getMessage))
          }
  }

  var notificationBuffer: mutable.Buffer[WorkflowDelta] = null

  /**
   * Send a message
   */
  def send[T <: WebsocketResponse](message: T)(implicit writes: Writes[T]) =
    session.getRemote.sendString(Json.stringify(Json.toJson(message)))

  /**
   * Delay out-of-band notifications until the enclosed block completes
   */
  def whilePausingNotifications[T](op: => T): T = 
  {
    val oldBuffer = notificationBuffer
    val myBuffer = mutable.Buffer[WorkflowDelta]()
    try {
      notificationBuffer = myBuffer
      op
    } finally {
      synchronized {
        myBuffer.foreach { delta => send(NotificationWebsocketMessage(delta)) }
        notificationBuffer = oldBuffer
      }
    }

  }

  /**
   * Post an out-of-band notification
   */
  def notify(delta: WorkflowDelta)
  {
    synchronized { 
      Option(notificationBuffer) match {
        case None => send(NotificationWebsocketMessage(delta))
        case Some(buffer) => buffer.append(delta)
      }
    }
  }

  object APIImpl 
    extends BranchWatcherAPIRoutes // <--- default routes implemented here
  {
    var projectId: Identifier = -1
    var branchId: Identifier = -1

    /**
     * Wrap the call
     */
    def wrapCall[T](action: String, op: => T): T = 
    {
      logger.debug(s"Processing Websocket Request: $action")
      whilePausingNotifications { op }
    }

    /**
     * Override get as a way to signal a subscription event
     */
    def subscribe(projectId: Identifier, branchId: Identifier) =
    {
      registerSubscription(projectId, branchId)
      this.branchId = branchId
      this.projectId = projectId
      workflowGet()
    }

    /**
     * No-op, just a way to get the system to acknowledge its existence and
     * keep the channel open
     */
    def ping() = System.currentTimeMillis()
  }

  object Router extends autowire.Server[JsValue, Reads, Writes]
  {
    def write[T: Writes](t: T) = Json.toJson(t)
    def read[T: Reads](s: JsValue): T = Json.fromJson[T](s).get

    // val routes = route[BranchHeadAPI](BranchHeadAPIImpl)
    val routes = route[BranchWatcherAPI](APIImpl)
  }
}

object BranchWatcherSocket
{
  val KEY_OPERATION = "operation"
  val OP_SUBSCRIBE = "subscribe"
  val OP_PING = "ping"
  val OP_PONG = "pong"

  object Creator extends WebSocketCreator
  {
    override def createWebSocket(
      request: ServletUpgradeRequest, 
      response: ServletUpgradeResponse
    ): Object = 
    {

      new BranchWatcherSocket()
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
