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
package info.vizierdb.ui

import org.scalajs.dom.document
import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.URLDecoder
import play.api.libs.json._

import info.vizierdb.api.spreadsheet.OpenDataset
import info.vizierdb.nativeTypes
import info.vizierdb.serialized.ProjectList
import info.vizierdb.serialized.PropertyList
import info.vizierdb.ui.components.dataset.Dataset
import info.vizierdb.ui.components.dataset.TableView
import info.vizierdb.ui.components.DisplayArtifact
import info.vizierdb.ui.components.MenuBar
import info.vizierdb.ui.components.Project
import info.vizierdb.ui.components.settings.SettingsView
import info.vizierdb.ui.components.StaticWorkflow
import info.vizierdb.ui.network.{ API, ClientURLs, BranchSubscription, SpreadsheetClient }
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.widgets.SystemNotification
import info.vizierdb.ui.widgets.Toast
import info.vizierdb.util.Logging
import scala.concurrent.Future
import scala.util.{ Try, Success, Failure }


/**
 * Methods for initializing each Vizier interface and managing global state
 * 
 * This class serves two roles:
 * 1. It contains a set of methods allowing easy access to global state (e.g., api, links)
 * 2. It contains one method for each 'page' of the UI that connects to the API and sets up
 *    the DOM.
 */
@JSExportTopLevel("Vizier")
object Vizier
  extends Object
  with Logging
{
  implicit val ctx = Ctx.Owner.safe() 

  var api:API = null
  var links:ClientURLs = null

  @JSExport("init")
  def init(url: String = "http://localhost:5000/") =
  {
    api = API(url+"vizier-db/api/v1")
    links = ClientURLs(url)
  }

  lazy val arguments: Map[String, String] = 
    Option(dom.window.location.search)
      .filter {  _.length > 0 }
      .map {
        _.substring(1)
         .split("&")
         .map { _.split("=").toSeq }
         .collect { 
            case Seq(k, v) => 
              URLDecoder.decode(k, "UTF-8") ->
                URLDecoder.decode(v, "UTF-8") 
          }
         .toMap
      }
      .getOrElse { Map.empty }

  val project = Var[Option[Project]](None)

  Rx {
    "Vizier" + project().map { project =>
      ": " + project.projectName() + " ["+ project.activeBranchName() + "]"
    }.getOrElse("")
  }.trigger { document.title = _ }

  val menu = project.map { _.map { new MenuBar(_) } }

  def error(message: String) =
  {
    Toast(s"ERROR: $message")
    // TODO: We should probably add a warning toast so that the user actually sees 
    //       that something has exploded.
    throw new Exception(message)
  }

  def main(args: Array[String]): Unit = 
  {

  }
  
  @JSExport("project_view")
  def projectView(): Unit    = roots.ProjectView(arguments = arguments)

  @JSExport("project_list")
  def projectList(): Unit    = roots.LandingPage(arguments = arguments)

  @JSExport("spreadsheet")
  def spreadsheet(): Unit    = roots.Spreadsheet(arguments = arguments)

  @JSExport("settings")
  def settings(): Unit       = roots.Settings(arguments = arguments)

  @JSExport("artifact")  
  def artifact(): Unit       = roots.ArtifactView(arguments = arguments)

  @JSExport("static_workflow")
  def staticWorkflow(): Unit = roots.StaticWorkflow(arguments = arguments)

  @JSExport("script_editor")
  def scriptEditor(): Unit   = roots.ScriptEditor(arguments = arguments)

}  
