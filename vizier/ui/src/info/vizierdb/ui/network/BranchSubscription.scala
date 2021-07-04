package info.vizierdb.ui.network

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
import info.vizierdb.ui.components.Artifact
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.util.Logging
import info.vizierdb.encoding

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

  var nextMessageId = 0l;
  val messageCallbacks = mutable.Map[Long, Promise[js.Dynamic]]()
  def withResponse(arguments: mutable.Map[String,Any]): Promise[js.Dynamic] =
    withResponse(arguments.toSeq:_*)
  def withResponse(arguments: (String, Any)*): Promise[js.Dynamic] =
  {
    if(!connected.now){
      throw new RuntimeException("Websocket not connected")
    }
    val messageId = nextMessageId
    nextMessageId = nextMessageId + 1
    val ret = Promise[js.Dynamic]()
    messageCallbacks.put(messageId, ret)
    socket.send(JSON.stringify(
      js.Dictionary(
        (arguments :+ ("messageId" -> messageId.toInt)):_*
      )
    ))
    ret
  }

  /**
   * Allocate a module and insert or append it into the workflow
   * @param command       A [[CommandDescription]] expressing the command
   * @param beforeModule  If Some(moduleId), insert before moduleId; if None, append.
   * @return              A future for the identifier of the inserted module.  The 
   *                      future is guaranteed to resolve before the corresponding 
   *                      cell insert event.
   */
  def allocateModule(
    command: encoding.ModuleCommand,
    atPosition: Option[Int]
  ): Future[Identifier] = 
  {
    val request = 
      command.asInstanceOf[js.Dictionary[Any]] ++ (
        atPosition match { 
          case None => 
            js.Dictionary("operation" -> "workflow.append")
          case Some(id) => 
            js.Dictionary("operation" -> "workflow.insert", "modulePosition" -> atPosition)
        }
      )
    withResponse(request)
      .future
      .map { x => 
        x.id.asInstanceOf[Identifier]
      }
  }

  /**
   * Delete a module already in the workflow
   * @param position     The position of the module to delete
   */
  def deleteModule(
    position: Int
  ): Future[Boolean] =
  {
    val request =
      js.Dictionary("operation" -> "workflow.delete", "modulePosition" -> position)
    withResponse(request)
      .future
      .map { x => true }
  }

  def onConnected(event: dom.Event)
  {
    connected() = true
    logger.debug("Connected!")
    awaitingReSync() = true
    socket.send(
      JSON.stringify(
        js.Dictionary(
          "operation" -> "subscribe",
          "projectId" -> projectId,
          "branchId" -> branchId
        )
      )
    )
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
  def onMessage(message: dom.MessageEvent) =
  {
    logger.trace(s"Got: ${message.data}")
    if(awaitingReSync.now){
      val base = JSON.parse(message.data.asInstanceOf[String])
                     .asInstanceOf[encoding.WorkflowDescription]
      logger.debug("Got initial sync")
      modules.clear()
      modules ++= base.modules
                      .zipWithIndex
                      .map { case (initialState, idx) =>
                        new ModuleSubscription(initialState, this, idx) 
                      }
      awaitingReSync() = false
    } else {
      val event = JSON.parse(message.data.asInstanceOf[String])
      logger.debug(s"Got Event: ${event.operation}")
      event.operation.asInstanceOf[String] match {
        case "response" => 
          messageCallbacks.remove(event.messageId.asInstanceOf[Int].toLong) match {
            case Some(promise) => promise.success(event)
            case None => logger.warn(s"Response to unsent messageId: ${event.messageId}")
          }

        case "insert_cell" => 
          val position = event.position.asInstanceOf[Int]
          modules.insert(
            position,
            new ModuleSubscription(
              event.cell.asInstanceOf[encoding.ModuleDescription], 
              this,
              position
            )
          )
          modules.drop(position)
                 .zipWithIndex
                 .foreach { case (module, offset) => module.position = position+offset }
        case "update_cell" => 
          val position = event.position.asInstanceOf[Int]
          modules.update(
            position,
            new ModuleSubscription(
              event.cell.asInstanceOf[encoding.ModuleDescription],
              this,
              position
            )
          )
        case "delete_cell" => 
          val position = event.position.asInstanceOf[Int]
          modules.remove(position)
          modules.drop(position-1)
                 .zipWithIndex
                 .foreach { case (module, offset) => module.position = position+offset }
        case "update_cell_state" =>
          logger.debug(s"State Update: ${event.state} @ ${event.position}")
          modules(event.position.asInstanceOf[Int]).state() = 
            ExecutionState(event.state.asInstanceOf[Int])
        case "append_cell_message" =>
          logger.debug(s"New Message")
          modules(event.position.asInstanceOf[Int])
            .messages += new encoding.StreamedMessage(
                            event.message.asInstanceOf[encoding.MessageDescription], 
                            StreamType(event.stream.asInstanceOf[Int])
                         )
        case "advance_result_id" => 
          logger.debug("Reset Result")
          val module = modules(event.position.asInstanceOf[Int])
          module.messages.clear()
          module.outputs() = Map[String,Artifact]()
        case "update_cell_outputs" => 
          val module = modules(event.position.asInstanceOf[Int])
          logger.debug(s"Adding outputs: ${event.outputs} -> ${module.outputs}")
          module.outputs() = 
            event.outputs.asInstanceOf[js.Array[encoding.ArtifactSummary]]
                         .map { artifact => 
                            logger.trace(s"Artifact: ${artifact.id}: ${artifact.category}")
                            artifact.name -> 
                              new Artifact(artifact)
                          }
                         .toMap
        case "pong" => ()
        case other => 
          logger.warn(s"Unknown operation $other\n$event")
      }
    }
  }
}