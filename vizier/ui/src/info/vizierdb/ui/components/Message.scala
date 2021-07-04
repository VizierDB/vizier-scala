package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import info.vizierdb.encoding
import info.vizierdb.ui.facades.Marked


sealed trait Message
  { def root: dom.Node }

case class TextMessage(text: String, clazz: String = "message") extends Message
{
  val root = 
    div(
      `class` := clazz,
      text.toString
          .split("\n")
          .map { div(_) }
    )
}
object TextMessage
{
  def error(text: String) = 
    TextMessage(text, "message error")
}

//////////////////////////////////////////////////////////////

case class DatasetMessage(content: Dataset) extends Message
{
  def root = content.root
}

//////////////////////////////////////////////////////////////

case class MarkdownMessage(content: String) extends Message
{
  val root:dom.html.Div = (div("Rendering..."):dom.Node).asInstanceOf[dom.html.Div]
  root.innerHTML = Marked(content)
}

//////////////////////////////////////////////////////////////


object Message
{
  def apply(message: encoding.StreamedMessage)
           (implicit owner: Ctx.Owner): Message =
  {
    message.t match {
      case "text/plain" => TextMessage(message.value.toString)
      case "dataset/view" => DatasetMessage(new Dataset(message.value.asInstanceOf[encoding.Dataset]))
      case "text/markdown" => MarkdownMessage(message.value.toString)
      case other => TextMessage.error(s"Unknown message type $other")
    }
  }
}