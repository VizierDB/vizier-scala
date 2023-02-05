package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount


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
    OnMount.traverse(modal)

  }

  def confirm(body: Frag*)(handler: => Unit): Unit =
    apply(body:_*)(
      button("Cancel", `class` := "cancel").render,
      button("OK", `class` := "confirm", onclick := { _:dom.Element => handler }).render
    )

  def acknowledge(body: Frag*): Unit =
    apply(body:_*)(
      button("OK", `class` := "confirm").render
    )
}