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
package info.vizierdb.ui.widgets

import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import info.vizierdb.ui.rxExtras.implicits._

class Expander(target: String, startOpen: Boolean = false)(implicit owner: Ctx.Owner)
{
  val state = Var[Boolean](false)
  val closedNode = FontAwesome("caret-right").render
  val openNode = FontAwesome("caret-down").render

  def open()  = { state() = true }
  def close() = { state() = false }
  def toggle() = { state() = !state.now }

  val root = 
    span(
      `class` := "expander", 
      state.map { case true => openNode ; case false => closedNode }.reactive,
      onclick := { _:dom.Event => state() = !state.now }
    ).render

  state.triggerLater { v => 
    val elem: dom.Element = dom.document.getElementById(target)
    if(v) { 
      elem.classList.add("open")
      elem.classList.remove("closed")
    } else {
      elem.classList.add("closed")
      elem.classList.remove("open")
    }
  }
}

object Expander
{
  def apply(target: String, startOpen: Boolean = false)(implicit owner: Ctx.Owner) =
    new Expander(target, startOpen)
}
