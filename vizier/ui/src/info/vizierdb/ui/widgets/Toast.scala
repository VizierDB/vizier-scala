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

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom

object Toast
{
  def apply(
    text: String, 
    level: String = "error", 
    summaryWidth: Int = 100,
    timeoutMs: Long = 10000
  ): dom.html.Element =
  {
    val lines = text.split("\n")
    val lead = lines.head
    val rest = lines.tail

    val node = div(`class` := s"toast $level",
      (if(lead.length <= summaryWidth){
        div(`class` := "summary", lead)
      } else {
        div(
          span(`class` := "summary",
            lead.take(summaryWidth-3)
          ),
          span(`class` := "summary_only", "..."),
          span(`class` := "detail", lead.drop(summaryWidth-3))
        )
      }),
      rest.map { div(`class` := "detail", _) }
    ).render
    
    dom.document.body.appendChild(node)

    dom.window.setTimeout(
      { () => 
        dom.document.body.removeChild(node)
      },
      timeoutMs
    )

    node
  }
}