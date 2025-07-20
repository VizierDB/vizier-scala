/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
import info.vizierdb.ui.widgets.ExternalDependencies
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.types._
import info.vizierdb.ui.network.SpreadsheetTools

sealed trait Message
  { def root: dom.Node }

case class TextMessage(text: String, clazz: String = "message text") extends Message
{
  // println(s"Text Message of type $clazz\n${text.take(200)}")
  val root = 
    div(
      `class` := clazz,
      text.toString
          .split("\n")
          .map { div(_) }
    ).render
}
object TextMessage
{
  def error(text: String) = 
    TextMessage(text, "message text error")
}

//////////////////////////////////////////////////////////////

case class DatasetMessage(content: serialized.DatasetDescription, module: Module)(implicit owner: Ctx.Owner) extends Message
{
  val dataset = new Dataset(
                  description = content, 
                  projectId = content.projectId,
                  onclick = { (_:Long, _:Int) => () },
                  menu = Dataset.DEFAULT_COMMANDS ++ Seq[Dataset.Command](
    (_:Identifier, _:Identifier, _:String) => {
      a(
        target := "_blank",
        FontAwesome("table"),
        onclick := { _:dom.Event =>
          if(module.nextRealModuleExcludingSelf.isEmpty){
            SpreadsheetTools.appendNewSpreadsheet(content.name)
          } else {
            SpreadsheetTools.insertNewSpreadsheet(content.name, module.position.now+1)
          }
        }
      )
    }
  ))
  val root = div(`class` := "message dataset", dataset.root).render
}

//////////////////////////////////////////////////////////////

case class MarkdownMessage(content: String) extends Message
{
  val root:dom.html.Div = 
    div(`class` := "message markdown", "Rendering...").render
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
    ).render
  ).asInstanceOf[dom.html.Div]
}

object VegaMessage
{
  var uniqueId = 0l
  def nextId: Long = { uniqueId = uniqueId + 1l; uniqueId }
}

//////////////////////////////////////////////////////////////

case class JavascriptMessage(code: String, html: String, js_deps: Option[Seq[String]], css_deps: Option[Seq[String]]) extends Message
{
  for(dep <- css_deps.toSeq.flatten){
    ExternalDependencies.loadCss(dep)
  }

  val root:dom.html.Div = 
    if(js_deps.isDefined){
      div(Spinner(), "Loading dependencies...").render
    } else {
      div(
        OnMount { _ => js.eval(code) }
      ).render
    }

  if(js_deps.isDefined){
    ExternalDependencies.loadJs(js_deps.get){ () => 
      println("Loaded!")
      root.innerHTML = html
      js.eval(code)
    }
  } else {
    root.innerHTML = html
  }
}
object JavascriptMessage
{

  implicit val format: Format[JavascriptMessage] = Json.format
}

//////////////////////////////////////////////////////////////


object Message
{
  def apply(message: serialized.MessageDescriptionWithStream, module: Module)
           (implicit owner: Ctx.Owner): Message =
  {
    message.t match {
      case MessageType.TEXT => TextMessage(message.value.as[String])
      case MessageType.HTML => DomMessage.html(message.value.as[String])
      case MessageType.PNG_IMAGE => DomMessage.png(message.value.as[String])
      case MessageType.MARKDOWN => MarkdownMessage(message.value.as[String])
      case MessageType.JAVASCRIPT => message.value.as[JavascriptMessage]
      case MessageType.DATASET => DatasetMessage(message.value.as[serialized.DatasetDescription], module)
      case MessageType.CHART => TextMessage.error(s"Chart messages not supported yet")
      case MessageType.VEGALITE => VegaMessage(message.value)
      case MessageType.VEGA => VegaMessage(message.value)
      case _ => TextMessage.error(s"Unknown message type ${message.t}")
    }
  }
}