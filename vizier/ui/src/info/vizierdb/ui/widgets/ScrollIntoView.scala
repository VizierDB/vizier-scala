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

import org.scalajs.dom.html
import scala.scalajs.js

object ScrollIntoView
{

  object Behavior extends Enumeration
  {
    type T = Value
    val Auto, Smooth = Value
  }
  object Block extends Enumeration
  {
    type T = Value
    val Start, Center, End, Nearest = Value
  }
  object Inline extends Enumeration
  {
    type T = Value
    val Start, Center, End, Nearest = Value
  }

  trait CanScroll
  {
    val root: html.Element
    def scrollIntoView(): Unit =
      ScrollIntoView(root, 
        behavior = ScrollIntoView.Behavior.Smooth, 
        block = ScrollIntoView.Block.Center
      )
  }

  private trait ScrollIntoViewOptions extends js.Object {
    var behavior: js.UndefOr[String] = js.undefined
    var block: js.UndefOr[String] = js.undefined
    var inline: js.UndefOr[String] = js.undefined
  }

  @js.native
  trait OptionedScrollIntoView extends js.Object {
    def scrollIntoView(options: ScrollIntoViewOptions): Unit
  }

  def apply(
    element: html.Element, 
    behavior: js.UndefOr[Behavior.T] = js.undefined,
    block: js.UndefOr[Block.T] = js.undefined,
    inline: js.UndefOr[Inline.T] = js.undefined,
  ) = 
  {
    val a = behavior
    val b = block
    val c = inline
    element.asInstanceOf[OptionedScrollIntoView]
           .scrollIntoView(new ScrollIntoViewOptions {
             behavior = a.map { _.toString.toLowerCase }
             block    = b.map { _.toString.toLowerCase }
             inline   = c.map { _.toString.toLowerCase }
           })
  }
}