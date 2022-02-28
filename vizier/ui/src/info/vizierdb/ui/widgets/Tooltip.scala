package info.vizierdb.ui.widgets

import scalatags.JsDom.all._

object Tooltip
{
  def apply(msg: String) = 
    tag("tooltip")(
      msg
    )
}