package info.vizierdb.ui.rxExtras

import org.scalajs.dom
import scalajs.js
import scalajs.js.annotation.JSExport
import rx._
import scalatags.JsDom.all._
import dom.html

package object implicits {

  def replaceNodeOnUpdate[T <% Frag](r: Rx[T], wrap: Frag => Frag = span(_))(implicit ctx: Ctx.Owner): Frag = {
    def rSafe: dom.Node = wrap(r.now).render
    var last = rSafe

    r.triggerLater {  
      val newLast = wrap(r.now).render
      last.parentNode.replaceChild(newLast, last)
      last = newLast
      OnMount.trigger(newLast)
    }
    OnMount.trigger(last)
    last
  }


  implicit def rxFrag[T <% Frag](r: Rx[T])(implicit ctx: Ctx.Owner): Frag =
    replaceNodeOnUpdate(r)
  
  implicit def renderFrag[T <% Frag](f: T): dom.Node = 
    f.render
}
