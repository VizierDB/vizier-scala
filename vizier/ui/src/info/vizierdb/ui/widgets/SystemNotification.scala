package info.vizierdb.ui.widgets

import org.scalajs.dom
import scala.collection.mutable
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
    val SLOW_CELL_FINISHED = Value
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
    apply(mode)("Notifications Enabled")
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
  {
    (BrowserNotification.permission == "granted") && (activeModes contains mode)
  }

  def save(): Unit =
  {
    dom.window.localStorage
              .setItem("SystemNotification", 
                activeModes.map { _.toString }
                           .mkString(";"))


  }

  def apply(mode: Mode.T)(text: String) =
  {
    if(isActive(mode)){
      new dom.experimental.Notification(
        "VizierDB", 
        dom.experimental.NotificationOptions(text)
      )
    }
  }
}