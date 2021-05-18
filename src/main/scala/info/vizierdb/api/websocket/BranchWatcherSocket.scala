package info.vizierdb.api.websocket

import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.annotations.{
  OnWebSocketClose,
  OnWebSocketConnect,
  OnWebSocketMessage,
  WebSocket,
}
import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog._
import info.vizierdb.delta.{ DeltaBus, WorkflowDelta }
import com.typesafe.scalalogging.LazyLogging

// https://git.eclipse.org/c/jetty/org.eclipse.jetty.project.git/tree/jetty-websocket/websocket-server/src/test/java/org/eclipse/jetty/websocket/server/examples/echo/ExampleEchoServer.java

case class SubscribeRequest(
  branchId: Identifier
)

object SubscribeRequest {
  implicit val format: Format[SubscribeRequest] = Json.format
}



@WebSocket
class BranchWatcherSocket
  extends LazyLogging
{
  var session: Session = null
  var subscription: DeltaBus.Subscription = null
  var branchId: Identifier = -1
  lazy val client = session.getRemoteAddress.toString

  @OnWebSocketConnect
  def onOpen(session: Session) 
  { 
    this.session = session 
  }
  @OnWebSocketClose
  def onClose(closeCode: Int, message: String) 
  {
    if(subscription != null){
      DeltaBus.unsubscribe(subscription)
    }
  }
  @OnWebSocketMessage
  def onText(data: String)
  {
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
          branchId = request.branchId
          subscription = DeltaBus.subscribe(request.branchId, this.notify, s"Websocket $client")
    }
  }

  def notify(delta: WorkflowDelta)
  {
    session.getRemote().sendString(
      Json.toJson(DB.readOnly { implicit s => 
        val branch = Branch.get(branchId)
        delta.serialize(workflowId = branch.headId, branchId = branch.id) 
      }).toString, 
      null
    )
  }
}

object BranchWatcherSocket
{
  val KEY_OPERATION = "operation"
  val OP_SUBSCRIBE = "subscribe"
}