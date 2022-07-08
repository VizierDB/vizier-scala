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

import scalikejdbc.DB
import scala.collection.mutable
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog._
import info.vizierdb.delta.{ DeltaBus, WorkflowDelta }
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.serialized
import info.vizierdb.serializers._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Success, Failure }
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import org.reactivestreams.Publisher

case class SubscribeRequest(
  projectId: String,
  branchId: String
)

object SubscribeRequest {
  implicit val format: Format[SubscribeRequest] = Json.format
}

class BranchWatcherSocket(client: String)(implicit system: ActorSystem, mat: Materializer)
  extends LazyLogging
{
  
  logger.debug(s"[$client] Websocket opened")

  // TODO: Review DeltaBus in the context of the actor system.  It may be
  // convenient to rewrite each branch as an actor (and on that note, we
  // might be able to get better performance around the whole JDBC business
  // if we revisit the internals as actors as well).  That's a substantial
  // rewrite, so for now, let's create an actor that will bridge deltabus to
  // the publisher API, which can then be Source-ified, as suggested here:
  // https://amdelamar.com/blog/websockets-with-akka-http/
  // https://github.com/amdelamar/akka-websockets-demo/blob/master/src/main/scala/com/amdelamar/chat/ChatRoom.scala
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
        if(subscription != null){
          DeltaBus.unsubscribe(subscription)
        }
      }})

  val flow = 
    Flow.fromSinkAndSource(sink, Source.fromPublisher(publisher))

  var subscription: DeltaBus.Subscription = null


  def registerSubscription(projectId: Identifier, branchId: Identifier): Unit = 
  {
    if(subscription != null){
      logger.warn(s"[$client] Websocket overriding existing subscription")
      DeltaBus.unsubscribe(subscription)
    }
    
    val branch = CatalogDB.withDBReadOnly { implicit s => Branch.get(projectId, branchId) } 
    
    subscription = DeltaBus.subscribe(branchId, this.notify, s"Websocket $client")
  }

  implicit val requestFormat = Json.format[WebsocketRequest]
  implicit val normalResponseWrites = Json.writes[NormalWebsocketResponse]
  implicit val errorResponseWrites = Json.writes[ErrorWebsocketResponse]
  implicit val notificationResponseWrites = Json.writes[NotificationWebsocketMessage]
  implicit val responseWrites = Json.writes[WebsocketResponse]

  def onText(data: String)
  {
    logger.trace(s"[$client] Websocket received ${data.length()} bytes: ${data.take(20)}")
    val request = Json.parse(data).as[WebsocketRequest]
    try { 
      logger.debug(s"[$client] Processing Websocket Request: ${request.path.last} (${Router.projectId} / ${Router.branchId})")
      whilePausingNotifications {
        val result = Router.route(request.path, request.args)
        send(NormalWebsocketResponse(request.id, result))
      }
    } catch {
      case error: Throwable =>
        logger.warn(s"[$client] Websocket error: $error")
        send(ErrorWebsocketResponse(request.id, error.getMessage.toString()))
    }
  }

  var notificationBuffer: mutable.Buffer[WorkflowDelta] = null

  /**
   * Send a message
   */
  def send(message: WebsocketResponse) =
  {
    logger.trace(s"[$client] SEND: ${message.toString.take(200)}")
    remote ! Json.stringify(Json.toJson(message))
  }

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
        logger.trace(s"[$client] Clearing buffer of ${myBuffer.size} messages")
        myBuffer.foreach { delta => 
          send(NotificationWebsocketMessage(delta)) }
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

  object Router 
    extends BranchWatcherAPIRoutes // <- default route implementations live here
  {
    var projectId: Identifier = -1
    var branchId: Identifier = -1

    /**
     * Override get as a way to signal a subscription event
     */
    def subscribe(projectId: Identifier, branchId: Identifier): serialized.WorkflowDescription =
    {
      registerSubscription(projectId, branchId)
      this.branchId = branchId
      this.projectId = projectId
      info.vizierdb.api.GetWorkflow(projectId = projectId, branchId = branchId)
    }

    /**
     * No-op, just a way to get the system to acknowledge its existence and
     * keep the channel open
     */
    def ping() = System.currentTimeMillis()

    override def route(path: Seq[String], args: Map[String, JsValue]) =
    {
      logger.trace(s"[$client] Request for ${path.mkString("/")}")
      path.last match {
        case "subscribe" => Json.toJson(subscribe(args("projectId").as[Identifier], args("branchId").as[Identifier]))
        case "ping" => Json.toJson(ping())
        case _ => super.route(path, args)
      }
    }
  }

  logger.trace("Websocket prepared")
}

object BranchWatcherSocket
{
  val KEY_OPERATION = "operation"
  val OP_SUBSCRIBE = "subscribe"
  val OP_PING = "ping"
  val OP_PONG = "pong"

  def monitor(client: String)(implicit system: ActorSystem, mat: Materializer): Flow[Message, Message, Any] =
    new BranchWatcherSocket(client).flow

}

