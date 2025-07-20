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

import org.scalajs.dom
import scalatags.JsDom.all._
import scalajs.js
import scala.collection.mutable

object OnMount
{
  private val ID = "data-vizier-on-mount"
  private var counter:Int = 0;
  private val triggers = mutable.Map[String, dom.Node => Unit]()

  def trigger(node: dom.Node)
  {
    if(isActuallyMounted(node)){ traverse(node) }
  }

  def isActuallyMounted(node: dom.Node): Boolean =
  {
    var curr = node
    while(curr != null){
      if(dom.document.body == curr){ return true }
      curr = curr.parentNode
    }
    return false
  }

  def traverse(node: dom.Node)
  {
    // println(s"Trigger: $node")
    for(child <- node.childNodes.asInstanceOf[js.Array[dom.Node]]) { 
      traverse(child) 
    }
    if(!node.attributes.equals(js.undefined)){
      // println(s"Checking attributes: ${node.attributes} (${node.attributes.length} elems)")
      // for(i <- 0 until node.attributes.length){
      //   println(node.attributes.item(i).name)
      // }
      if(node.attributes.hasOwnProperty(ID)){
        val id = node.attributes.getNamedItem(ID).value.asInstanceOf[String]
        node.attributes.removeNamedItem(ID)
        triggers.remove(id).foreach { _ .apply(node) }
      }
    }
  }

  def apply(op: dom.Node => Unit): AttrPair =
  {
    counter += 1
    triggers.put(counter.toString, op)
    attr(ID) := counter.toString
  }

}