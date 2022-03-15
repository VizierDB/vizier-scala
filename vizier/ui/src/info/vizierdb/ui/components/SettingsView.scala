package info.vizierdb.ui.components

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom

class SettingsView
{

  val root = 
    div(`class` := "settings",
      div(`class` := "setting",
        h2("Apache Spark"),
        a(href := "localhost:4040", "Spark Web UI")
      ),

    )
}