package info.vizierdb.ui.rxExtras

import scala.collection.mutable
import org.scalajs.dom
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import scalatags.JsDom.all._
import scalatags.JsDom

class RxBufferView(val root: dom.Node)(implicit owner: Ctx.Owner, data: Ctx.Data)
  extends SimpleRxBufferWatcher[dom.Node]
{
  val nodes = mutable.Buffer[dom.Node]()

  def onAppend(elem: dom.Node): Unit =
  {
    println(s"View Append: $elem -> $root")
    val node: dom.Node = elem
    nodes += node
    root.appendChild(node)
  }

  def onClear(): Unit = 
  {
    println("View Clear")
    nodes.clear()
    while(root.hasChildNodes()){
      root.removeChild(root.firstChild)
    }
  }

  def onPrepend(elem: dom.Node): Unit =
  {
    println("View Prepend")
    val node: dom.Node = elem
    node +=: nodes
    if(root.hasChildNodes()){
      root.insertBefore(node, root.firstChild)
    } else {
      root.appendChild(node)
    }
  }

  def onRemove(n: Int): Unit = 
  {
    println("View Remove")
    val node = nodes(n)
    root.removeChild(node)
    nodes.remove(n)
  }

  def onUpdate(n: Int, elem: dom.Node): Unit =
  {
    println("View Update")
    val node: dom.Node = elem
    val oldNode = nodes(n)
    root.replaceChild(node, oldNode)
    nodes.update(n, node)
  }

  def onInsert(n: Int, elem: dom.Node): Unit = 
  {
    println("View Insert")
    val node: dom.Node = elem
    val children = root.childNodes
    if(n+1 >= children.length){
      root.appendChild(node)
    } else {
      root.insertBefore(node, children(n+1))
    }
    nodes.insert(n, node)
  }
}

object RxBufferView
{
  def apply(root: dom.Node, source: RxBuffer[dom.Node])
           (implicit owner: Ctx.Owner, data: Ctx.Data): RxBufferView =
  {
    val ret = new RxBufferView(root)
    for(node <- source){ ret.onAppend(node) }
    source.watch(ret)
    return ret
  }
}