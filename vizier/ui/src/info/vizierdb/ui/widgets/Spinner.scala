package info.vizierdb.ui.widgets

import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.rxExtras._

// Modeled after bootstrap
// https://getbootstrap.com/docs/4.3/components/spinners/
object Spinner
{
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
}