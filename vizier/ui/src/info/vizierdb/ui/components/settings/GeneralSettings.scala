package info.vizierdb.ui.components.settings

import rx._
import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.Vizier
import scala.util.Failure
import scala.util.Success
import info.vizierdb.ui.widgets.SystemNotification

class GeneralSettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val title = "General"

  object Notifications
  {
    val enabled = input(
          id := "notifications_enabled", 
          `type` := "checkbox",
          onchange := { _:dom.Event => save() }
        ).render

    def load(): Unit =
    {
      enabled.checked = SystemNotification.isActive(SystemNotification.Mode.SLOW_CELL_FINISHED)
    }

    def save(): Unit =
    {
      if(enabled.checked){
        SystemNotification.activate(SystemNotification.Mode.SLOW_CELL_FINISHED)
      } else {
        SystemNotification.deactivate(SystemNotification.Mode.SLOW_CELL_FINISHED)
      }
    }
  }

  def load(): Unit =
  {
    Notifications.load()
  }

  val root = div(`class` := "general",
    div(`class` := "group",
      div(`class` := "title", "Notifications"),
      div(`class` := "setting",
        label(`for` := "notifications_enabled", "... when a slow cell finishes"),
        Notifications.enabled,
      ),      
    ),
  ).render
}