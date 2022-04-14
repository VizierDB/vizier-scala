package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._

object ShowModal
{
  def apply(body: Frag*)(buttons: dom.html.Button*): Unit =
  {

    val modal =
      div(
        `class` := "modal",
        div(
          `class` := "body",
          body,
          div(
            `class` := "buttons",
            buttons
          )
        ),
      ).render

    buttons.foreach { _.addEventListener("click", { 
      _:dom.Event => dom.document.body.removeChild(modal) 
    }) }

    dom.document.body.appendChild(modal)

  }
}