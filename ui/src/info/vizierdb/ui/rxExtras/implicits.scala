package info.vizierdb.ui.rxExtras

import org.scalajs.dom
import scalajs.js
import scalajs.js.annotation.JSExport
import rx._
import scalatags.JsDom.all._
import dom.html

package object implicits {

  implicit def rxFrag[T <% Frag](r: Rx[T])(implicit ctx: Ctx.Owner, data: Ctx.Data): Frag = {
    def rSafe: dom.Node = span(r()).render
    var last = rSafe

    r.triggerLater {  
      val newLast = rSafe
      last.parentNode.replaceChild(newLast, last)
      last = newLast
    }
    last
  }
  
  implicit def renderFrag[T <% Frag](f: T): dom.Node = 
    f.render
}
