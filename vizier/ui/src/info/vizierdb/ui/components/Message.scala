package info.vizierdb.ui.components

import play.api.libs.json._
import org.scalajs.dom
import scala.scalajs.js
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import info.vizierdb.serialized
import info.vizierdb.ui.facades.{ Marked, VegaEmbed }
import info.vizierdb.types.MessageType
import info.vizierdb.serializers._
import info.vizierdb.ui.components.dataset.Dataset
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.nativeTypes.JsValue

sealed trait Message
  { def root: dom.Node }

case class TextMessage(text: String, clazz: String = "message") extends Message
{
  // println(s"Text Message of type $clazz\n${text.take(200)}")
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
  val root = div(`class` := "message", content.root)
}

//////////////////////////////////////////////////////////////

case class MarkdownMessage(content: String) extends Message
{
  val root:dom.html.Div = (div("Rendering..."):dom.Node).asInstanceOf[dom.html.Div]
  root.innerHTML = Marked(content)
}

//////////////////////////////////////////////////////////////

class DomMessage extends Message
{
  // println(s"Allocating HtmlMessage\n$content")
  val root:dom.html.Div = div(`class` := "message").render
}
object DomMessage
{
  def html(content: String): DomMessage =
  {
    val ret = new DomMessage; ret.root.innerHTML = content; ret
  }
  def png(content: String): DomMessage =
  {
    val ret = new DomMessage
    ret.root.appendChild(
      img(
        src := "data:image/png;base64,"+content
      ).render
    )
    ret
  }
}

//////////////////////////////////////////////////////////////

case class VegaMessage(content: JsValue) extends Message
{
  val divId = s"vega_chart_${VegaMessage.nextId}"
  val root:dom.html.Div = (
    div(
      OnMount { node => 
        VegaEmbed(
          s"#$divId", 
          playToNativeJson(content).asInstanceOf[js.Dictionary[Any]]
        )
      },
      id := divId,
    ):dom.Node
  ).asInstanceOf[dom.html.Div]
}

object VegaMessage
{
  var uniqueId = 0l
  def nextId: Long = { uniqueId = uniqueId + 1l; uniqueId }
}

//////////////////////////////////////////////////////////////

case class JavascriptMessage(code: String, html: String, js_deps: Option[Seq[String]]) extends Message
{
  val root:dom.html.Div = 
    // if(js_deps.isEmpty) {
      div(
        OnMount { _ => js.eval(code) }
      ).render
  //   } else {
  //     div(
  //       s"This module depends on ${js_deps.mkString(", ")}"
  //     ).render
  //   }

  // if(js_deps.isEmpty){
    root.innerHTML = html
  // }
}
object JavascriptMessage
{

  implicit val format: Format[JavascriptMessage] = Json.format
}

//////////////////////////////////////////////////////////////


object Message
{
  def apply(message: serialized.MessageDescriptionWithStream)
           (implicit owner: Ctx.Owner): Message =
  {
    message.t match {
      case MessageType.TEXT => TextMessage(message.value.as[String])
      case MessageType.HTML => DomMessage.html(message.value.as[String])
      case MessageType.PNG_IMAGE => DomMessage.png(message.value.as[String])
      case MessageType.MARKDOWN => MarkdownMessage(message.value.as[String])
      case MessageType.JAVASCRIPT => message.value.as[JavascriptMessage]
      case MessageType.DATASET => DatasetMessage(new Dataset(message.value.as[serialized.DatasetDescription]))
      case MessageType.CHART => TextMessage.error(s"Chart messages not supported yet")
      case MessageType.VEGALITE => VegaMessage(message.value)
      case _ => TextMessage.error(s"Unknown message type ${message.t}")
    }
  }
}