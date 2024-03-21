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

object PopUpButton
{
  def apply(
    trigger: dom.html.Button,
    rest: dom.html.Button*
  ): Frag =
  {
    trigger.classList.add("trigger")
    rest.foreach { _.classList.add { "pop_up_button" } }

    val container:dom.html.Div =
      div(
        `class` := s"pop_up_container closed size${rest.size}",
        trigger,
        rest
      ).render

    trigger.addEventListener("click", { _:dom.Event => 
      if(container.classList.contains("open")){
        container.classList.remove("open")
        container.classList.add("closed")
      } else {
        container.classList.remove("closed")
        container.classList.add("open")
      }
    })

    rest.foreach { _.addEventListener("click", { _:dom.Event => 
      container.classList.remove("open")
      container.classList.add("closed")
    })}

    return container
  }


}