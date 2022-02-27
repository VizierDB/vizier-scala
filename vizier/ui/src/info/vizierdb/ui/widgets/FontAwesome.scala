package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._

object FontAwesome
{
  /**
   * The FontAwesome key from https://fontawesome.com/v4/icons/
   * @param   key   the icon key (without the fa- prefix)
   */
  @inline
  def apply(key: String) =
    i(`class` := s"fa fa-${key}", attr("aria-hidden") := "true")
}