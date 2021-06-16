package info.vizierdb.ui.view

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.state.Message
import rx._

object MessageView
{
  def apply(message: Message): dom.Node =
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