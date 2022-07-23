package info.vizierdb.ui.components.settings

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom

class PythonSettings(parent: SettingsView) extends SettingsTab
{

  def title = "Python"
  val root = div(`class` := "api_keys",
    div(`class` := "settings_block", "NOTHING HERE"
    )
  ).render
  def load(): Unit = {}
}