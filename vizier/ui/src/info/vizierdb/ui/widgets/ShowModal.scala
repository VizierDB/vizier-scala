/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._

object ShowModal
{
  def apply(body: Frag*)(buttons: dom.html.Button*): Unit =
  {

    val modal =
      div(
        `class` := "modal",
        div(
          `class` := "body",
          body,
          div(
            `class` := "buttons",
            buttons
          )
        ),
      ).render

    buttons.foreach { _.addEventListener("click", { 
      _:dom.Event => dom.document.body.removeChild(modal) 
    }) }

    dom.document.body.appendChild(modal)

  }

  def confirm(body: Frag*)(handler: => Unit): Unit =
    apply(body:_*)(
      button("Cancel", `class` := "cancel").render,
      button("OK", `class` := "confirm", onclick := { _:dom.Element => handler }).render
    )

  def acknowledge(body: Frag*): Unit =
    apply(body:_*)(
      button("OK", `class` := "confirm").render
    )
}