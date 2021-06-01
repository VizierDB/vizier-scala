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
import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog._
import info.vizierdb.delta.{ DeltaBus, WorkflowDelta }
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.jetty.websocket.servlet.WebSocketServlet

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
  var session: Session = null
  var subscription: DeltaBus.Subscription = null
  var branchId: Identifier = -1
  lazy val client = session.getRemoteAddress.toString

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
  @OnWebSocketMessage
  def onText(data: String)
  {
    logger.trace(s"Websocket received ${data.length()} bytes: ${data.take(20)}")
    val message = Json.parse(data)
    (message \ BranchWatcherSocket.KEY_OPERATION).asOpt[String]
       .getOrElse { 
         logger.error(s"Invalid operation in websocket ($client) message: ${data.take(300)}")
       } match {
         case BranchWatcherSocket.OP_SUBSCRIBE => 
          val request = message.as[SubscribeRequest]
          if(subscription != null){
            logger.warn(s"Websocket ($client) overriding existing subscription")
            DeltaBus.unsubscribe(subscription)
          }
          branchId = request.branchId.toLong
          subscription = DeltaBus.subscribe(branchId, this.notify, s"Websocket $client")
          val workflow = DB.readOnly { implicit s => 
                              Branch.get(branchId)
                                    .head
                                    .describe }
          session.getRemote()
                 .sendString(Json.toJson(workflow).toString)
    }
  }

  def notify(delta: WorkflowDelta)
  {
    session.getRemote().sendString(
      Json.toJson(delta).toString, 
      null
    )
  }
}

object BranchWatcherSocket
{
  val KEY_OPERATION = "operation"
  val OP_SUBSCRIBE = "subscribe"

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

