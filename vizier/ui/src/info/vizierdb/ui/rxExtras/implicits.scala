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
