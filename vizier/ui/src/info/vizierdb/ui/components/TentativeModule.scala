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
package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import info.vizierdb.types.ArtifactType
import info.vizierdb.serialized
import scala.concurrent.{ Future, Promise }
import info.vizierdb.types.Identifier
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.util.Trie
import info.vizierdb.ui.widgets.ScrollIntoView
import info.vizierdb.ui.widgets.Tooltip

class TentativeModule(
  val editList: TentativeEdits, 
  val id_attr: String,
  defaultPackageList: Option[Seq[serialized.PackageDescription]] = None
)(implicit owner: Ctx.Owner)
  extends WorkflowElement
  with NoWorkflowOutputs
  with ModuleEditorDelegate
  with ScrollIntoView.CanScroll
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
  var defaultModule: Option[(String, String)] = None

  val activeView = Var[Option[Either[CommandList, ModuleEditor]]](None)
  val selectedDataset = Var[Option[String]](None)
  var id: Option[Identifier] = None

  val editor: Rx[Option[ModuleEditor]] = 
    Rx { activeView() match {
      case None => None
      case Some(Left(_)) => None
      case Some(Right(ed)) => Some(ed)
    } }

  def setTentativeModuleId(newId: Identifier) = id = Some(newId)
  def tentativeModuleId = id
  def realModuleId = None



  loadPackages()

  def selectCommand(packageId: String, command: serialized.PackageCommand)
  {
    activeView() = Some(Right(ModuleEditor(packageId, command, this)))
  }
  def cancelSelectCommand(): TentativeModule =
  {
    editList.dropTentative(this)
    return this
  }
  def setDefaultModule(packageId: String, commandId: String): TentativeModule = 
  {
    defaultModule = Some((packageId, commandId))
    return this
  }
  def showCommandList(packages: Seq[serialized.PackageDescription])
  {
    defaultModule match {
      case None => 
        activeView() = Some(Left(new CommandList(packages, this)))
      case Some( (packageId, commandId) ) => 
        selectCommand(
          packageId,
          packages.find { _.id == packageId }
                  .getOrElse { Vizier.error(s"Couldn't load package '$packageId'") }
                  .commands
                  .find { _.id == commandId }
                  .getOrElse { Vizier.error(s"Couldn't load command '$commandId'") }
        )
        defaultModule = None
    }
  }

  def cancelEditor()
  {
    activeView() = None
    loadPackages()
  }

  def loadPackages()
  {
    defaultPackageList match {
      case Some(packages) => 
        showCommandList(packages)
      case None =>
        Vizier
          .api
          .workflowHeadSuggest(
            editList.project.projectId,
            editList.project.activeBranch.now.get,
          )
          .onSuccess { case packages => 
            showCommandList(packages)
          }
    }
  }

  def client = 
    editList
      .project
      .branchSubscription
      .getOrElse { Vizier.error("No Connection!") }
      .Client


  // def nextModule(): Option[Identifier] =
  // {
  //   editList.elements
  //           .drop(position)
  //           .find { _.isLeft }
  //           .collect { case Left(m) => m.id }
  // }

  val root = div(`class` := "module tentative",
    attr("id") := id_attr,
    div(
      `class` := "menu",
      // button("x"),
      div(`class` := "spacer")
    ),
    div(
      `class` := "module_body",
      activeView.map {
        case None => b("Loading commands...")
        case Some(Left(commandList)) => commandList.root
        case Some(Right(editor)) => editor.root
      }.reactive
    )
  ).render
}
