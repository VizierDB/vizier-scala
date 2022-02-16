package info.vizierdb.ui.rxExtras

import org.scalajs.dom
import scalajs.js
import scalajs.js.annotation.JSExport
import rx._
import scalatags.JsDom.all._
import dom.html

class RxTagWrapper[T <% Frag](r: Rx[T])(implicit ctx: Ctx.Owner)
{
  def reactive: Frag =
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
    last    
  }
}


package object implicits {

  implicit def rxFrag[T <% Frag](r: Rx[T])(implicit ctx: Ctx.Owner): RxTagWrapper[T] =
    new RxTagWrapper[T](r)
  
  implicit def renderFrag[T <% Frag](f: T): dom.Node = 
    f.render
}
