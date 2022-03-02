package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._

object ConfirmableButton
{
  def apply(
    buttonContents: Frag*
  )(
    onConfirm: dom.MouseEvent => Unit
  ): Frag =
  {
    val startButton = button(buttonContents:_*).render
    val endButton = button(`class` := "confirm", FontAwesome("check")).render

    val container:dom.html.Div = 
      div(
        `class` := "confirmable_button_wrapper closed",
        startButton,
        div(`class` := "confirmable_button",
          endButton
        )
      ).render

    startButton.addEventListener("click", { e:dom.MouseEvent => 
      if(container.classList.contains("open")){
        container.classList.remove("open")
        container.classList.add("closed")
      } else {
        container.classList.remove("closed")
        container.classList.add("open")
      }
    })
    endButton.addEventListener("click", { e:dom.MouseEvent =>
      container.classList.remove("open")
      container.classList.add("closed")
      onConfirm(e)
    })
    return container
  }


}