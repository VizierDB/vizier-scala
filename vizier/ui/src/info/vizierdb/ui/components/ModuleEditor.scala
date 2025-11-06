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
import info.vizierdb.util.PluginLoader
import scala.util.Try
import info.vizierdb.util.FrontendPlugin
import scala.scalajs.js.annotation._

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
  def selectedDataset = Var[Option[String]](None)

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
      case (pkgId, cmdId) => {
        println(s"plugin check => pkg: ${pkgId}, cmd: ${cmdId}")
        PluginModuleEditor(pkgId, command, delegate)
      }
      case _ => new DefaultModuleEditor(packageId, command, delegate)
    }
  }
}

@js.native
trait PluginCommandRegistration extends js.Object {
  def commandId:String
  def beginDisplay(artifacts:Seq[String], state:Seq[Parameter]):Seq[CommandArgument]
  def endDisplay: Seq[CommandArgument] 
  def editorFields:dom.Element 
}

trait PluginCommandRegistrationSjs {
  def commandId:String
  def beginDisplay(artifacts:Seq[String], state:Seq[Parameter]):Seq[CommandArgument]
  def endDisplay: Seq[CommandArgument] 
  def editorFields:dom.Element 
}

object PluginModuleEditor {
  def pluginCommandEditors(
    packageId:String, 
    command: serialized.PackageCommand, 
    delegate: ModuleEditorDelegate
  )(implicit owner: Ctx.Owner): Option[PluginModuleEditor] = 
    PluginLoader.loadedPlugins.get(packageId) match {
      case Some(pkg) => {
        /*(try {
          println(s"type of plugin PluginCommandEditorRegistration ${pkg}")
          val raw = js.eval(s"$packageId")
          // Defensive: Check if defined and has the right method(s)
          (if (!js.isUndefined(raw) && js.typeOf(raw) == "object")
            Some(raw.asInstanceOf[FrontendPlugin])
          else
            None)
          .map(frontendPlugin => {
            //val pkgcmded = pkg.getPluginCommandEditor(packageId, command, delegate)
            //println(s"result of plugin PluginCommandEditorRegistration ${pkgcmded}")
            println(s"type of eval PluginCommandEditorRegistration ${frontendPlugin}")
            val pkgcmded = frontendPlugin.getPluginCommandEditor(packageId, command.id)
            println(s"result of plugin PluginCommandEditorRegistration: ")
            pkgcmded
          })
        }
        catch {
          case t:Throwable => {
            println(s"$t -> ${t.getStackTrace().mkString("\n")}")
            None
          }
        })*/ Try(pkg.pluginCommandEditor(command.id).asInstanceOf[PluginCommandRegistration]).toOption match {
          case None => {
            println(s"NOT loading plugin command ${packageId} ${command.id}")
            None
          }
          case pce => {
            if(pkg.asInstanceOf[js.Dynamic].pluginCommandEditorIds.asInstanceOf[Seq[String]].contains(command.id)){
              val resolvedPce = pce.get
              Some(new PluginModuleEditor(packageId, command, delegate, resolvedPce))
            }
            else None
          }
        }
      }
      case None => None
    }

  def apply(
      packageId: String, 
      command: serialized.PackageCommand, 
      delegate: ModuleEditorDelegate
    )(implicit owner: Ctx.Owner): ModuleEditor = {
      pluginCommandEditors(packageId, command, delegate) match {
        case Some(pe) => pe
        case _ => new DefaultModuleEditor(packageId, command, delegate)
      }
    }
    //val cachedPluginModuleEditors: scala.collection.mutable.Map[(String, String), PluginModuleEditor] = scala.collection.mutable.Map()
}


class PluginModuleEditor(
  val packageId: String, 
  val command: serialized.PackageCommand, 
  val delegate: ModuleEditorDelegate,
  val pluginEditor: PluginCommandRegistration
)(implicit owner: Ctx.Owner)  extends ModuleEditor
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

  def commandId() = {
    command.id
  }

  override val selectedDataset = Var[Option[String]](None)

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
 
  def visibleArtifacts = delegate.visibleArtifacts.now.keySet.toSeq

  lazy val getParameter:Map[String, Parameter] = 
    parameters.map { p => p.id -> p }.toMap

  /*js.eval(s"""${packageId}.getPluginCommandEditor("${packageId}", "${command.id}").stateBegin""")
    .asInstanceOf[js.Function1[Any,Seq[CommandArgument]]].apply(parameters.map(_.value))*/
    
  //pluginEditor.stateBegin( parameters )

  def currentState: Seq[CommandArgument] =
    parameters.map { _.toArgument }

  val editorFields = {
    try{
      //println(s"PluginModuleEditor.editorFields result: ${pluginEditor.editorFields}")
      pluginEditor.beginDisplay(visibleArtifacts,  parameters )
      //pluginEditor.asInstanceOf[js.Dynamic].applyDynamic("beginDisplay")(parameters)   
      //(pluginEditor.beginDisplay _).asInstanceOf[js.Function1[Seq[Parameter], Unit]].apply(parameters)
      /*js.eval(s"""${packageId}.getPluginCommandEditor("${packageId}","${command.id}").stateBeginJS;""")
        .asInstanceOf[(Seq[Parameter]) => Seq[CommandArgument]].apply(parameters)*/
    }
    catch {
      case tr:Throwable => println(s"problem setting plugin editor state: ${tr}/n ${tr.getStackTrace().mkString("\n")}")
    }
    //val eff = js.eval(s"""${packageId}.getPluginCommandEditor("${packageId}", "${command.id}").editorFields()""").asInstanceOf[dom.raw.Element]
    val eff = (pluginEditor.editorFields)
    println(s"PluginModuleEditor.editorFields result: ${eff}")

     div(
      width := "100%",
      // h4(command.name),
      eff,
      parameters.filter { !_.hidden }
                .map { param => div(width := "100%", param.root) }
    )
    
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

  override val selectedDataset = Var[Option[String]](None)

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