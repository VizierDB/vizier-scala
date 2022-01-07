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