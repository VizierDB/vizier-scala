/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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

  object SlowCell
  {
    val enabled = input(
          id := "slow_notifications_enabled", 
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
  object Error
  {
    val enabled = input(
          id := "err_notifications_enabled", 
          `type` := "checkbox",
          onchange := { _:dom.Event => save() }
        ).render

    def load(): Unit =
    {
      enabled.checked = SystemNotification.isActive(SystemNotification.Mode.ON_ERROR)
    }

    def save(): Unit =
    {
      if(enabled.checked){
        SystemNotification.activate(SystemNotification.Mode.ON_ERROR)
      } else {
        SystemNotification.deactivate(SystemNotification.Mode.ON_ERROR)
      }
    }
  }

  def load(): Unit =
  {
    SlowCell.load()
  }

  val root = div(`class` := "general",
    div(`class` := "group",
      div(`class` := "title", "Notifications"),
      div(`class` := "setting",
        label(`for` := "slow_notifications_enabled", "... when a slow cell finishes"),
        SlowCell.enabled,
      ),      
      div(`class` := "setting",
        label(`for` := "err_notifications_enabled", "... when an error occurs"),
        Error.enabled,
      ),      
    ),
  ).render
}