package info.vizierdb.ui.components.settings

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom

object APIKeys extends SettingsTab
{

  val title = "Remote APIs"

  object OSM
  {
    val enabled = input(id := "osm_enabled", `type` := "checkbox").render
    val url = input(
          id := "osm_url", 
          `type` := "text",
          placeholder := "https://your.server.org/search"
        )

    def save(evt: dom.Event): Unit =
    {
      println("Saving OSM")
    }
  }

  object Google
  {
    val enabled = input(id := "google_enabled", `type` := "checkbox")
    val apikey = input(
          id := "google_api", 
          `type` := "text",
          placeholder := "a4db08b7-5729-[this is a sample]-f2df493465a1"
        )

    def save(evt: dom.Event): Unit =
    {
      println("Saving Google")
    }
  }

  val root = div(`class` := "api_keys",
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
      button(`class` := "save",
        onclick := OSM.save _,
        "Save OSM"
      )
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
      button(`class` := "save",
        onclick := Google.save _,
        "Save Google"
      )
    ),
  ).render
}