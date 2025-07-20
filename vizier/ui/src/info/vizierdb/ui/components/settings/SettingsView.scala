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
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.Vizier
import scala.util.Failure
import scala.util.Success

class SettingsView(initialTab: Option[String] = None)(implicit owner: Ctx.Owner)
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  val tabs = Seq[SettingsTab](
    new GeneralSettings(this),
    new APIKeySettings(this),
    new PythonSettings(this)
  )

  val activeTabIdx = Var[Option[Int]](None)
  val activeTab = activeTabIdx.map { _.map { tabs(_) } }

  def switchTo(slug: String): Unit =
  {
    switchTo(tabs.indexWhere { _.slug == slug })
  }

  def switchTo(idx: Int): Unit =
  {
    if(idx >= 0 && idx < tabs.size){ 
      activeTabIdx() = Some(idx) 
      dom.window.history.replaceState(null, "", Vizier.links.settings(tabs(idx).slug))
    }
  }

  var registry: Map[String, String] = Map.empty

  Vizier.api
        .configGetRegistry()
        .onComplete {
          case Failure(err) => Vizier.error(err.getMessage())
          case Success(incoming) => registry = incoming
                                    tabs.foreach { _.load() }
                                    initialTab match {
                                      case Some(slug) => switchTo(slug)
                                      case None => activeTabIdx() = Some(0)
                                    }
        }

  def updateRegistry(key: String, value: String)(onSuccess: () => Unit)
  {
    Vizier.api.configSetRegistryKey(key, value)
          .onComplete {
            case Failure(err) => Vizier.error(err.toString())
            case Success(_) => 
              registry = registry ++ Map(key -> value)
              onSuccess()
          }
  }


  val root = 
    div(`class` := "settings",
      div(`class` := "tabs",
        tabs.zipWithIndex.map { case (tab, idx) =>
          Rx { 
            if(activeTabIdx().isDefined && activeTabIdx().get == idx) { 
              div(`class` := "tab active", tab.title,
                onclick := { evt:dom.Event => evt.stopPropagation() }
              )
            } else {
              div(`class` := "tab inactive", tab.title, 
                onclick := { evt:dom.Event => switchTo(idx); evt.stopPropagation() }
              )
            }
          }.reactive
        },
        div(`class` := "spacer")
      ),
      div(`class` := "content",
        activeTab.map { 
          case None => div(`class` := "loading", Spinner(30)).render
          case Some(content) => content.root
        }.reactive
      ),
    ).render
}