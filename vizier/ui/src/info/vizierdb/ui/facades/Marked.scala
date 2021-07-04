package info.vizierdb.ui.facades

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom

object Marked
{
  @js.native
  @JSGlobal("marked")
  def apply(text: String): String = js.native
}
