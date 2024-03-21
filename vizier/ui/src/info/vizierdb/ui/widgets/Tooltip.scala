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

object Tooltip {
  lazy val tooltip = {
    val tt =
      div(`class` := "tooltip", style := "visibility: hidden;", "???").render
    dom.window.document.body.appendChild(tt)
    /* return */
    tt
  }
  private var triggerHandle: Option[Int] = None

  def show(x: Double, y: Double)(content: dom.Node*): Unit = {
    tooltip.style.left = s"${x}px"
    tooltip.style.top = s"${y}px"
    while (tooltip.firstChild != null) {
      tooltip.removeChild(tooltip.firstChild)
    }
    content.foreach { tooltip.appendChild(_) }
    clearTrigger()
    tooltip.style.visibility = "visible"
    tooltip.style.opacity = "1.0"
  }

  def showSoon(x: Double, y: Double)(content: dom.Node*): Unit = {
    clearTrigger()
    triggerHandle = Some(
      dom.window.setTimeout(
        { () =>
          show(x, y)(content: _*)
        },
        1000
      )
    )
  }

  def showSoon(evt: dom.MouseEvent)(content: dom.Node*): Unit = {
    showSoon(evt.pageX + 20, evt.pageY + 20)(content: _*)
  }

  def hide(): Unit = {
    clearTrigger()
    tooltip.style.visibility = "hidden"
    tooltip.style.opacity = "0"
    // while(tooltip.firstChild != null){
    //   tooltip.removeChild(tooltip.firstChild)
    // }
  }

  def hideSoon(): Unit = {
    clearTrigger()
    triggerHandle = Some(
      dom.window.setTimeout(
        { () =>
          hide()
        },
        100
      )
    )
  }

  def clearTrigger(): Unit = {
    if (triggerHandle.isDefined) {
      dom.window.clearTimeout(triggerHandle.get)
      triggerHandle = None
    }
  }

  def apply(msg: String): Seq[AttrPair] =
    apply(span(msg))

  def apply(elem: Frag*): Seq[AttrPair] =
    make(elem.map { _.render }: _*)

  def make(content: dom.Node*): Seq[AttrPair] =
    Seq(
      onmouseover := { (evt: dom.MouseEvent) =>
        showSoon(evt)(content: _*)
      },
      onmouseout := { (_: dom.Event) =>
        hideSoon()
      }
    )

  val root = div(
    `class` := "side_menu_content",
    style := "position: absolute; display: none;"
  ).render
}

