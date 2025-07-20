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
package info.vizierdb.ui.components.snippets

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.types._
import info.vizierdb.ui.widgets.FontAwesome
import scala.collection.mutable.ArrayBuffer

abstract class SnippetsBase
{
  val groups = ArrayBuffer[Group]()

  case class Group(icon: String, label: String, snippets: Seq[Snippet])
  case class Snippet(label: String, snippet: String)

  def AddGroup(icon: String, label: String)(snippets: (String, String)*): Unit =
    groups.append(Group(icon, label, snippets.map { case (n, s) => Snippet(n, s) }))

  /**
   * Sequence of (Icon, Label, Seq(Name, Snippet))
   */

  def apply(handler: String => Unit) =
  {
    val container = div(`class` := "snippet_container", 
        div(`class` := "snippets",
          groups.map { case Group(icon, label, snippets) =>
            div(`class` := "group",
              div(`class` := "label", FontAwesome(icon), label),
              snippets.map { case Snippet(name, snippet) =>
                div(`class` := "snippet",
                  name,
                  onclick := { _:dom.Event => handler(snippet) }
                )
              }
            )
          }
        )
      ).render

    container.insertBefore(
      div(`class` := "header",
        span(`class` := "toggle", FontAwesome("caret-right")), 
        "Code Snippets",
        onclick := { _:dom.Event =>
          container.classList.toggle("expanded")
        },
      ).render,
      container.firstChild
    )

    container
  }


}