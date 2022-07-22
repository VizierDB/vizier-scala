package info.vizierdb.ui.components.settings

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom

object PythonSettings extends SettingsTab
{

  def title = "Python"
  def root = div(`class` := "api_keys",
    div(`class` := "settings_block", "NOTHING HERE"
    )
  ).render
}