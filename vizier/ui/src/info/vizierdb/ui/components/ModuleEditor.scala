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
import rx._
import scala.scalajs.js
import info.vizierdb.serialized
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.util.Logging
import info.vizierdb.serializers._
import info.vizierdb.api.websocket
import info.vizierdb.types.ArtifactType
import info.vizierdb.serialized.{ 
  CommandArgument, 
  CommandArgumentList, 
  CommandDescription, 
  ParameterDescriptionTree,
  DatasetSummary,
  DatasetDescription,
  DatasetColumn,
  PackageCommand
}
import info.vizierdb.types._
import info.vizierdb.nativeTypes.JsValue
import scala.util.{ Success, Failure }
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.network.BranchWatcherAPIProxy
import info.vizierdb.ui.components.editors._
import info.vizierdb.ui.widgets.FontAwesome

trait ModuleEditor
  extends Object
  with Logging
{
  def saveState()
  {
    val response = 
      if(delegate.realModuleId.isDefined) {
        delegate.client.workflowReplace(
          modulePosition = delegate.insertPosition,
          packageId = packageId,
          commandId = commandId,
          arguments = currentState
        )
      } else if(delegate.needsAppendToInsert){
        delegate.client.workflowAppend(
          packageId = packageId,
          commandId = commandId,
          arguments = currentState
        )
      } else {
        delegate.client.workflowInsert(
          modulePosition = delegate.insertPosition,
          packageId = packageId,
          commandId = commandId,
          arguments = currentState
        )
      }
    response.onComplete { 
      case Success(workflow) =>
        logger.trace("SUCCESS!")
        if(workflow.actionModule.isDefined){
          logger.trace(s"has action module: ${workflow.actionModule}")
          delegate.setTentativeModuleId(workflow.actionModule.get)
        } else {
          logger.debug(s"no action module... falling back: ${workflow.modules.size}")
          delegate.setTentativeModuleId(workflow.modules(delegate.insertPosition).moduleId)
        }
        logger.debug(s"New module id is... ${delegate.tentativeModuleId}")
      case f:Failure[_] =>
        logger.trace("REQUEST FAILED!")
    }
  }

  def setState(arguments: (String, JsValue)*) =
    loadState(CommandArgumentList(arguments:_*))
  
  def loadState(arguments: Seq[CommandArgument])
  def packageId: String
  def commandId: String
  def delegate: ModuleEditorDelegate
  def currentState: Seq[CommandArgument]
  val editorFields: Frag

  def serialized: CommandDescription =
    CommandDescription(
      packageId = packageId,
      commandId = commandId,
      arguments = currentState
    )

  lazy val root: Frag = 
    div(`class` := "module_editor",
      editorFields,
      div(`class` := "editor_actions",
        button(FontAwesome("arrow-left"), " Back", `class` := "cancel", onclick := { (e: dom.MouseEvent) => delegate.cancelEditor() }),
        div(`class` := "spacer"),
        button(FontAwesome("cogs"), " Save", `class` := "save", onclick := { (e: dom.MouseEvent) => saveState() })
      )
    )
}

object ModuleEditor
{
  def apply(
    packageId: String, 
    command: serialized.PackageCommand, 
    delegate: ModuleEditorDelegate
  )(implicit owner: Ctx.Owner): ModuleEditor = {
    (packageId, command.id) match {
      case ("data", "load")   => new LoadDatasetEditor(delegate)
      case ("data", "unload") => new UnloadDatasetEditor(delegate)
      case _ => new DefaultModuleEditor(packageId, command, delegate)
    }
  }
}



class DefaultModuleEditor(
  val packageId: String, 
  val command: serialized.PackageCommand, 
  val delegate: ModuleEditorDelegate
)(implicit owner: Ctx.Owner) 
  extends ModuleEditor
  with Logging
{

  def loadState(arguments: Seq[CommandArgument])
  {
    for(arg <- arguments){
      getParameter.get(arg.id) match {
        case Some(parameter) => parameter.set(arg.value)
        case None => logger.warn(s"Load state with undefined parameter: ${arg.id}")
      }
    }
  }

  def commandId = command.id

  val selectedDataset = Var[Option[String]](None)

  val parameters: Seq[Parameter] = 
    ParameterDescriptionTree(
      command.parameters.toSeq
    ).map { Parameter(_, this) }

  parameters.collect { 
    case dsParam:ArtifactParameter if dsParam.artifactType == ArtifactType.DATASET => dsParam 
  }.headOption match {
    case None => ()
    case Some(dsParameter) => 
      dsParameter.selectedDataset.trigger {
        selectedDataset() = dsParameter.selectedDataset.now
      }
  }

  lazy val getParameter:Map[String, Parameter] = 
    parameters.map { p => p.id -> p }.toMap

  def currentState: Seq[CommandArgument] =
    parameters.map { _.toArgument }


  val editorFields =
    div(
      width := "100%",
      // h4(command.name),
      parameters.filter { !_.hidden }
                .map { param => div(width := "100%", param.root) }
    )
}

trait ModuleEditorDelegate
{
  def client: BranchWatcherAPIProxy
  def cancelEditor(): Unit
  def realModuleId: Option[Identifier]
  def tentativeModuleId: Option[Identifier]
  def setTentativeModuleId(newId: Identifier): Unit
  def insertPosition: Int
  def needsAppendToInsert: Boolean
  def visibleArtifacts: Rx[Map[String, (serialized.ArtifactSummary, WorkflowElement)]]
}