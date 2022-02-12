package info.vizierdb.ui.facades

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom

object VegaEmbed
{
  @js.native
  @JSGlobal("vegaEmbed")
  def apply(divId: String, spec: js.Dictionary[Any]): Unit = js.native
}
