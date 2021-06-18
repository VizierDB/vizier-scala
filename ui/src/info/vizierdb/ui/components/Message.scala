package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.network.StreamedMessage
import rx._

object Message
{
  def apply(message: StreamedMessage): dom.Node =
  {
    message.t match {
      case "text/plain" => 
        div(
          message.value.toString
                 .split("\n")
                 .map { div(_) }
        )
      case other => div(s"Unknown message type $other")
    }
  }
}