package info.vizierdb.ui.state

import org.scalajs.dom
import info.vizierdb.ui.API
import rx._
import scala.scalajs.js
import scala.scalajs.js.JSON
import info.vizierdb.ui.rxExtras.RxBufferVar
import info.vizierdb.types._
import scala.scalajs.js.timers._

class BranchSubscription(branchId: Identifier, projectId: Identifier, api: API)
{
  var socket = getSocket() 
  var awaitingReSync = false
  var keepaliveTimer: SetIntervalHandle = null

  val connected = Var(false)
  val modules = new RxBufferVar[ModuleSubscription]()

  private def getSocket(): dom.WebSocket =
  {
    println(s"Connecting to ${api.urls.websocket}")
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
    println("Connected!")
    awaitingReSync = true
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
    println(s"Error: $event")
  }
  def onMessage(message: dom.MessageEvent) =
  {
    // println(s"Got: ${event.data}")
    if(awaitingReSync){
      val base = JSON.parse(message.data.asInstanceOf[String])
                     .asInstanceOf[WorkflowDescription]
      println("Got initial sync")
      modules.clear()
      modules ++= base.modules
                      .map { new ModuleSubscription(_) }
      awaitingReSync = false
    } else {
      val event = JSON.parse(message.data.asInstanceOf[String])
      println(s"Got Event: ${event.operation}")
      event.operation.asInstanceOf[String] match {
        case "insert_cell" => 
          modules.insert(
            event.position.asInstanceOf[Int],
            new ModuleSubscription(event.cell.asInstanceOf[ModuleDescription])
          )
        case "update_cell" => 
          modules.update(
            event.position.asInstanceOf[Int],
            new ModuleSubscription(
              event.cell.asInstanceOf[ModuleDescription]
            )
          )
        case "delete_cell" => 
          modules.remove(
            event.position.asInstanceOf[Int]
          )
        case "update_cell_state" =>
          println(s"State Update: ${event.state} @ ${event.position}")
          modules(event.position.asInstanceOf[Int]).state() = 
            ExecutionState(event.state.asInstanceOf[Int])
        case "append_cell_message" =>
          println(s"New Message")
          modules(event.position.asInstanceOf[Int])
            .messages += new Message(
                            event.message.asInstanceOf[MessageDescription], 
                            StreamType(event.stream.asInstanceOf[Int])
                         )
        case "advance_result_id" => 
          println("Reset Result")
          modules(event.position.asInstanceOf[Int]).messages.clear()
        case "pong" => ()
        case other => 
          println(s"Unknown operation $other\n$event")
      }
    }
  }
}