package info.vizierdb.ui.facades

import scala.scalajs.js
import org.scalajs.dom
import scala.scalajs.js.annotation.JSGlobal


@js.native
@JSGlobal("CodeMirror")
object CodeMirror extends js.Object
{
  def fromTextArea(textArea: dom.Node): Unit = js.native
  def fromTextArea(textArea: dom.Node, config: js.Dictionary[Any]): Unit = js.native
}