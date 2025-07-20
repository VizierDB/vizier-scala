/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.ui.widgets

import org.scalajs.dom
import scala.collection.mutable
import scalatags.JsDom.all._
import java.{util => ju}
import dom.experimental.{ Notification => BrowserNotification }
import info.vizierdb.ui.Vizier

/**
 * Wrapper around the browser's tray notification facility.  Allows for specific classes
 * of notifications to be enabled/disabled individually, and persists these changes in
 * the browser's local storage.
 */
object SystemNotification
{
  object Mode extends Enumeration
  {
    type T = Value
    val SLOW_CELL_FINISHED, 
        ON_ERROR = Value

    def describe(v: T) =
      v match {
        case SLOW_CELL_FINISHED => "when a slow cell finishes"
        case ON_ERROR => "when an error occurs"
      }
  }

  val activeModes:mutable.Set[Mode.T] = 
    dom.window.localStorage
              .getItem("SystemNotification") match {
                case null | "" => mutable.Set.empty
                case x => mutable.Set(x.split(";").flatMap { n =>
                                        try { 
                                          Some(Mode.withName(n)) 
                                        } catch {
                                          case _:ju.NoSuchElementException => None
                                        }
                                      }:_*)
              }

  def activate(mode: Mode.T): Unit =
  {
    if(!checkPermissions()){ return }
    activeModes += mode
    save()
    apply(mode)(s"Notifications ${Mode.describe(mode)} enabled")
  }

  def deactivate(mode: Mode.T): Unit =
  {
    activeModes -= mode
    save()
  }

  def checkPermissions(): Boolean =
  {
    if(BrowserNotification.permission != "granted"){
      BrowserNotification.requestPermission(_ match {
                                case "granted" => return true
                                case _ => Vizier.error("I didn't get permission to notify you")
                             })
      return false
    } else {
      return true
    }
  }

  def isActive(mode: Mode.T): Boolean =
    (activeModes contains mode)

  def browserNotificationsEnabled: Boolean =
    (BrowserNotification.permission == "granted")
  
  def save(): Unit =
  {
    dom.window.localStorage
              .setItem("SystemNotification", 
                activeModes.map { _.toString }
                           .mkString(";"))


  }

  def apply(mode: Mode.T)(text: String) =
  {
    if(isActive(mode) && browserNotificationsEnabled){
      new dom.experimental.Notification(
          "VizierDB", 
          dom.experimental.NotificationOptions(text)
        )
    }
  }
}