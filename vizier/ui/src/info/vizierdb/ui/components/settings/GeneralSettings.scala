package info.vizierdb.ui.components.settings

import rx._
import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.rxExtras.implicits._
import dom.experimental.{ Notification => BrowserNotification }
import info.vizierdb.ui.Vizier
import scala.util.Failure
import scala.util.Success

class GeneralSettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val title = "General"

  object Notifications
  {
    val enabled = input(
          id := "notifications_enabled", 
          `type` := "checkbox",
          onchange := { _:dom.Event => changed() = true }
        ).render

    val changed = Var[Boolean](false)

    def load(): Unit =
    {
      println(s"Browser permission: ${BrowserNotification.permission}")
      enabled.checked = (
        (BrowserNotification.permission == "granted") 
        && parent.registry.get("notifications")
                          .map { _ == "on" }
                          .getOrElse { false }
      )
    }

    def save(): Unit =
    {
      def persist(cfg: String) =
      {
        Vizier.api.configSetRegistryKey("notifications", cfg)
                  .onComplete {
                    case Failure(err) => Vizier.error(err.toString())
                    case Success(_) => changed() = false
                  }
      }

      if(enabled.checked){
        if(BrowserNotification.permission != "granted"){
          BrowserNotification.requestPermission(_ match {
                                case "granted" => persist("on")
                                case _ => 
                                  enabled.checked = false
                                  changed() = false
                                  Vizier.error("Didn't get permission to notify")
                             })
        } else { persist("on") }
      } else {
        persist("off")
      }
    }
  }

  object OSM
  {
    val enabled = input(
          id := "osm_enabled", 
          `type` := "checkbox",
          onchange := { _:dom.Event => changed() = true }
        ).render
    val url = input(
          id := "osm_url", 
          `type` := "text",
          placeholder := "https://your.server.org/search",
          onchange := { _:dom.Event => changed() = true }
        ).render

    val changed = Var[Boolean](false)

    def load(): Unit =
    {
      parent.registry.get("osm_url") match {
        case None =>    enabled.checked = false
                        url.value = ""
        case Some(v) => enabled.checked = true
                        url.value = v
      }
    }

    def save(evt: dom.Event): Unit =
    {
      println("Saving OSM")
    }
  }

  object Google
  {
    val enabled = input(
          id := "google_enabled", 
          `type` := "checkbox",
          onchange := { _:dom.Event => changed() = true }
        ).render
    val apikey = input(
          id := "google_api", 
          `type` := "text",
          placeholder := "a4db08b7-5729-[this is a sample]-f2df493465a1",
          onchange := { _:dom.Event => changed() = true }
        ).render

    val changed = Var[Boolean](false)

    def load(): Unit =
    {
      parent.registry.get("google_key") match {
        case None =>    enabled.checked = false
                        apikey.value = ""
        case Some(v) => enabled.checked = true
                        apikey.value = v
      }
    }

    def save(evt: dom.Event): Unit =
    {
      println("Saving Google")
    }
  }

  def load(): Unit =
  {
    OSM.load()
    Google.load()
    Notifications.load()
  }

  val root = div(`class` := "general",
    div(`class` := "group",
      div(`class` := "title", "Notifications"),
      div(`class` := "setting",
        label(`for` := "notifications_enabled", "... when a slow cell finishes"),
        Notifications.enabled,
      ),      
      Rx { 
        button(
          `class` := (if(Notifications.changed()) { "save changed" } else { "save" }),
          onclick := { _:dom.Event => Notifications.save() },
          "Save Notifications"
        )
      }.reactive
    ),
    div(`class` := "group",
      div(`class` := "title", "Open Street Map"),
      div(`class` := "description",
        a(href := "https://www.openstreetmap.org/", "Open Street Map", target := "_blank"),
        " is a map of the world, created by people like you.  Vizier can use an Open Street Map ",
        a(href := "https://nominatim.org/", "Nominatem", target := "_blank"),
        " server to translate addresses into GPS coordinates."
      ),
      div(`class` := "setting",
        label(`for` := "osm_enabled", "Enabled"),
        OSM.enabled,
      ),
      div(`class` := "setting",
        label(`for` := "osm_url", "Nominatem Search URL"),
        OSM.url,
      ),
      Rx { 
        button(
          `class` := (if(OSM.changed()) { "save changed" } else { "save" }),
          onclick := OSM.save _,
          "Save OSM"
        )
      }.reactive
    ),
    div(`class` := "group",
      div(`class` := "title", "Google"),
      div(`class` := "description",
        "Vizier can integrate with Google in a variety of ways, including loading data directly from Google Sheets or Geocoding addresses.  ",
        "To use Google with Vizier, you must provide an ",
        a(href := "https://cloud.google.com/docs/authentication/api-keys", "API key", target := "_blank"),
        "."
      ),
      div(`class` := "setting",
        label(`for` := "google_enabled", "Enabled"),
        Google.enabled,
      ),
      div(`class` := "setting",
        label(`for` := "google_api", "API Key"),
        Google.apikey,
      ),
      Rx { 
        button(
          `class` := (if(Google.changed()) { "save changed" } else { "save" }),
          onclick := Google.save _,
          "Save Google"
        )
      }.reactive
    ),
  ).render
}