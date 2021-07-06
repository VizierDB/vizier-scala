package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import info.vizierdb.serialized
import info.vizierdb.ui.facades.Marked
import info.vizierdb.types.MessageType
import info.vizierdb.serializers._

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

case class HtmlMessage(content: String) extends Message
{
  val root:dom.html.Div = (div(""):dom.Node).asInstanceOf[dom.html.Div]
  root.innerHTML = content
}

//////////////////////////////////////////////////////////////


object Message
{
  def apply(message: serialized.MessageDescriptionWithStream)
           (implicit owner: Ctx.Owner): Message =
  {
    message.t match {
      case MessageType.TEXT => TextMessage(message.value.toString)
      case MessageType.HTML => HtmlMessage(message.value.toString)
      case MessageType.MARKDOWN => MarkdownMessage(message.value.toString)
      case MessageType.JAVASCRIPT => TextMessage.error(s"Javascript messages not supported yet")
      case MessageType.DATASET => DatasetMessage(new Dataset(message.value.as[serialized.DatasetDescription]))
      case MessageType.CHART => TextMessage.error(s"Chart messages not supported yet")
    }
  }
}