package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._

object PopUpButton
{
  def apply(
    trigger: dom.html.Button,
    rest: dom.html.Button*
  ): Frag =
  {
    trigger.classList.add("trigger")
    rest.foreach { _.classList.add { "pop_up_button" } }

    val container:dom.html.Div =
      div(
        `class` := s"pop_up_container closed size${rest.size}",
        trigger,
        rest
      ).render

    trigger.addEventListener("click", { _:dom.Event => 
      if(container.classList.contains("open")){
        container.classList.remove("open")
        container.classList.add("closed")
      } else {
        container.classList.remove("closed")
        container.classList.add("open")
      }
    })

    rest.foreach { _.addEventListener("click", { _:dom.Event => 
      container.classList.remove("open")
      container.classList.add("closed")
    })}

    return container
  }


}