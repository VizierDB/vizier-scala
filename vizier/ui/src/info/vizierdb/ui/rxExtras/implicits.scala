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
package info.vizierdb.ui.rxExtras

import org.scalajs.dom
import scalajs.js
import scalajs.js.annotation.JSExport
import rx._
import scalatags.JsDom.all._
import dom.html

class RxTagWrapper[T <% Frag](r: Rx[T])(implicit ctx: Ctx.Owner)
{
  /**
   * Convert an Rx-wrapped fragment to a reactive DOM node
   * 
   * The fragment will return a &lt;span&gt; node.  Whenever
   * the reactive component is updated, the span node's 
   * contents will be replaced by the updated fragment.
   */
  def reactive: dom.Node =
  {
    def rSafe: dom.Node = r.now.render
    var last = rSafe

    r.triggerLater {  
      val newLast = r.now.render
      last.parentNode.replaceChild(newLast, last)
      last = newLast
      OnMount.trigger(newLast)
    }
    OnMount.trigger(last)

    // wrap the node in a span to ensure that it has a parent
    // **before** it shows up in the DOM.  Without this, we'll
    // potentially get a null pointer exception above if 
    span(`class` := "reactive", last).render
  }
}


package object implicits {

  implicit def rxFrag[T <% Frag](r: Rx[T])(implicit ctx: Ctx.Owner): RxTagWrapper[T] =
    new RxTagWrapper[T](r)
  
  implicit def renderFrag[T <% Frag](f: T): dom.Node = 
    f.render
}
