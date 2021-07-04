package info.vizierdb.ui.facades

import scala.scalajs.js
import org.scalajs.dom
import scala.scalajs.js.annotation.JSGlobal


@js.native
@JSGlobal("CodeMirror")
object CodeMirror extends js.Object
{
  def fromTextArea(textArea: dom.Node): CodeMirrorEditor = js.native
  def fromTextArea(textArea: dom.Node, config: js.Dictionary[Any]): CodeMirrorEditor = js.native
}

@js.native
trait CodeMirrorEditor extends js.Object
{
  def getValue(): String = js.native
  def getValue(separator: String): String = js.native
  def setValue(content: String): String = js.native
}