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

  def replaceSelection(replacement: String): Unit = js.native
  def replaceSelection(replacement: String, select: String): Unit = js.native

  def focus(): Unit = js.native

  def on(evtType: String, func: js.Function1[Any,Any]): Unit = js.native
  def on(evtType: String, func: js.Function2[Any,Any,Any]): Unit = js.native
}

@js.native
trait CodeMirrorPosition extends js.Object
{
  val ch: Int = js.native
  val line: Int = js.native
}

@js.native
trait CodeMirrorChangeObject extends js.Object
{
  val from: CodeMirrorPosition = js.native
  val to: CodeMirrorPosition = js.native
  val text: Array[String] = js.native
  val removed: String = js.native
}

