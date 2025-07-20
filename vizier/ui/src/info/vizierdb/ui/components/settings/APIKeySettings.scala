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

class APIKeySettings(parent: SettingsView)(implicit owner: Ctx.Owner) extends SettingsTab
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val title = "API Keys"

  object OSM
  {
    val url = input(
          id := "osm_url", 
          `type` := "text",
          placeholder := "https://your.server.org/search",
          onchange := { _:dom.Event => changed() = true },
          onkeyup := { _:dom.Event => changed() = true }
        ).render

    val changed = Var[Boolean](false)

    def load(): Unit =
    {
      url.value = parent.registry.getOrElse("osm-server", "")
    }

    def save(evt: dom.Event): Unit =
    {
      parent.updateRegistry("osm-server", url.value)( () => changed() = false )
    }
  }

  object Google
  {
    val apikey = input(
          id := "google_api", 
          `type` := "text",
          placeholder := "a4db08b7-5729-[this is a sample]-f2df493465a1",
          onchange := { _:dom.Event => changed() = true },
          onkeyup := { _:dom.Event => changed() = true }
        ).render

    val changed = Var[Boolean](false)

    def load(): Unit =
    {
      apikey.value = parent.registry.getOrElse("google-api-key", "")
    }

    def save(evt: dom.Event): Unit =
    {
      parent.updateRegistry("google-api-key", apikey.value)( () => changed() = false )
    }
  }

  def load(): Unit =
  {
    OSM.load()
    Google.load()
  }

  val root = div(`class` := "api_keys",
    div(`class` := "group",
      div(`class` := "title", "Open Street Map"),
      div(`class` := "description",
        a(href := "https://www.openstreetmap.org/", "Open Street Map", target := "_blank"),
        " is a map of the world, created by people like you.  Vizier can use an Open Street Map ",
        a(href := "https://nominatim.org/", "Nominatem", target := "_blank"),
        " server to translate addresses into GPS coordinates.  ",
        b("Leave blank to disable.")
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
        ".  ",
        b("Leave blank to disable.")
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