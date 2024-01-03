package info.vizierdb.ui.roots

import info.vizierdb.ui.components.settings.SettingsView
import org.scalajs.dom
import org.scalajs.dom.document
import rx._
import info.vizierdb.ui.rxExtras.OnMount
import scalatags.JsDom.all._

object Settings
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val settings = new SettingsView(arguments.get("tab"))
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild(settings.root)
      OnMount.trigger(document.body)
    })
  }


}