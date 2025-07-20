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
package info.vizierdb.ui.rxExtras

import scala.collection.mutable
import org.scalajs.dom
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import scalatags.JsDom.all._
import scalatags.JsDom
import info.vizierdb.util.Logging

class RxBufferView(val root: dom.Node)
                  (implicit owner: Ctx.Owner)
  extends SimpleRxBufferWatcher[dom.Node]
  with Logging
{
  val nodes = mutable.Buffer[dom.Node]()

  def onAppend(elem: dom.Node): Unit =
  {
    logger.debug(s"View Append: $elem -> $root")
    val node: dom.Node = elem
    nodes += node
    root.appendChild(node)
    OnMount.trigger(node)
  }

  def onClear(): Unit = 
  {
    logger.debug("View Clear")
    nodes.clear()
    while(root.hasChildNodes()){
      root.removeChild(root.firstChild)
    }
  }

  def onPrepend(elem: dom.Node): Unit =
  {
    logger.debug("View Prepend")
    val node: dom.Node = elem
    node +=: nodes
    if(root.hasChildNodes()){
      root.insertBefore(node, root.firstChild)
    } else {
      root.appendChild(node)
    }
    OnMount.trigger(node)
  }

  def onRemove(n: Int): Unit = 
  {
    logger.debug("View Remove")
    val node = nodes(n)
    root.removeChild(node)
    nodes.remove(n)
  }

  def onUpdate(n: Int, elem: dom.Node): Unit =
  {
    logger.debug("View Update")
    val node: dom.Node = elem
    val oldNode = nodes(n)
    root.replaceChild(node, oldNode)
    nodes.update(n, node)
    OnMount.trigger(node)
  }

  def onInsert(n: Int, elem: dom.Node): Unit = 
  {
    logger.debug("View Insert")
    val node: dom.Node = elem
    val children = root.childNodes
    logger.trace(children.toString)
    if(n >= children.length){
      root.appendChild(node)
    } else {
      root.insertBefore(node, children(n))
    }
    nodes.insert(n, node)
    OnMount.trigger(node)
  }
}

object RxBufferView
{
  def apply(root: dom.Node, source: RxBuffer[dom.Node])
           (implicit owner: Ctx.Owner): RxBufferView =
  {
    val ret = new RxBufferView(root)
    for(node <- source){ ret.onAppend(node) }
    source.deliverUpdatesTo(ret)
    return ret
  }
}