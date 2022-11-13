package info.vizierdb.ui.widgets

import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.rxExtras._
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import info.vizierdb.ui.Vizier

// Modeled after bootstrap
// https://getbootstrap.com/docs/4.3/components/spinners/
object Spinner
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def apply(size: Int = 15): dom.Node =
    span(
      `class` := "spinner",
      width  := s"${size}px",
      height := s"${size}px",
      marginLeft := "auto",
      marginRight := "auto",
      display := "inline-block",
      css("animation-name") := "spinner-rotate",
      css("animation-duration") := "1s",
      css("animation-timing-function") := "linear",
      css("animation-delay") := "0s",
      css("animation-iteration-count") := "infinite",
      css("animation-direction") := "normal",
      css("animation-fill-mode") := "none",
      css("animation-play-state") := "running",
      border := ".25em solid black",
      borderRightColor := "transparent",
      borderRadius := "50%",
      ""
    ).render

  def lazyLoad(op: Future[dom.Node]): dom.Node =
  {
    val spinner = apply().render
    val body = span(`class` := "lazyload", spinner).render

    op.onComplete { 
      case Success(r) => body.replaceChild(r, spinner) 
      case Failure(err) => body.replaceChild(
          div(`class` := "error_result", err.toString()).render,
          spinner
        )
        Vizier.error(err.getMessage())
    }

    body
  }

}