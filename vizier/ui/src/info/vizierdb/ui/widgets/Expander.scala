package info.vizierdb.ui.widgets

import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import info.vizierdb.ui.rxExtras.implicits._

object Expander
{
  def apply(target: String, startOpen: Boolean = false)(implicit owner: Ctx.Owner): dom.Node =
  {
    val state = Var(startOpen)
    val closed = FontAwesome("caret-right").render
    val open = FontAwesome("caret-down").render
    val me = 
      span(
        `class` := "expander", 
        state.map { case true => open ; case false => closed }.reactive,
        onclick := { _:dom.Event => state() = !state.now }
      )
    state.triggerLater { v => 
      val elem: dom.Element = dom.document.getElementById(target)
      if(v) { 
        elem.classList.add("open")
        elem.classList.remove("closed")
      } else {
        elem.classList.add("closed")
        elem.classList.remove("open")
      }
    }
    return me
  }
}
