package info.vizierdb.ui.components.settings

import org.scalajs.dom

trait SettingsTab
{
  def title: String
  val root: dom.html.Element
  def load(): Unit

  lazy val slug: String = 
    title.replaceAll("[^A-Za-z0-9_]+", "_").toLowerCase
}