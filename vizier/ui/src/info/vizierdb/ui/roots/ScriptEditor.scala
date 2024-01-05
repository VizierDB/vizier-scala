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
package info.vizierdb.ui.roots

import rx._
import info.vizierdb.ui.Vizier
import org.scalajs.dom.document
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.widgets.Spinner
import scala.concurrent.Future
import info.vizierdb.serialized.VizierScript
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.serialized.VizierScriptModule
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.RxBufferVar
import info.vizierdb.serialized.ArtifactSummary
import info.vizierdb.types._

class ScriptEditor(script: VizierScript)(implicit owner: Ctx.Owner)
{

  val modules: RxBufferVar[VizierScriptModule] = 
    RxBuffer.ofSeq(script.modules)

  var activeOutputs = Var[Set[String]](Set.empty)

  // The last module optionally stores the activated outputs of the script.  
  // Strip this off and initialize activeOutputs if so
  modules.last match {
    case module: VizierScriptModule.InputOutput if module.imports.isEmpty =>
      modules.remove(modules.size-1)
      activeOutputs() = module.exports.keySet
    case _ => ()
  }

  val (inputs, outputs) =
    modules.asVar.map { case (_, modules) => {
      val (missingInputs, activeOutputs, _) = 
        modules.foldLeft( (
          Map[String, ArtifactType.T](), // Missing Inputs
          Map[String, ArtifactType.T](), // Active Outputs
          Map[String, ArtifactType.T](), // All Outputs
        ) ){ 
          case ( (missingInputs, activeOutputs, allOutputs), module ) => 
            if(module.enabled){
              (
                missingInputs ++ 
                  (module.inputs -- activeOutputs.keySet)
                    .map { attr =>
                      attr -> allOutputs.getOrElse(attr, ArtifactType.PARAMETER)
                    }.toMap, 
                activeOutputs ++ module.outputs,
                allOutputs ++ module.outputs,
              )
            } else {
              (
                missingInputs, 
                activeOutputs,
                allOutputs ++ module.outputs
              )
            }
        }
      (missingInputs, activeOutputs)
    }}.unzip


  def toggleEnabled(moduleId: Identifier): Unit =
  {
    for((module, idx) <- modules.zipWithIndex)
    {
      module match {
        case module:VizierScriptModule.Inline if module.id == moduleId => 
          modules.update(idx, module.copy(enabled = !module.enabled))
        case _ => ()
      }
    }
  }

  def nextIOId:Identifier =
    modules.foldLeft(-1l){ (id, mod) => Math.min(id, mod.id) } - 1

  def addStaticInput(name: String, artType: ArtifactType.T)
  {
    if(modules.head.isInstanceOf[VizierScriptModule.InputOutput] && modules.head.id < 0)
    {
      val old = modules.head.asInstanceOf[VizierScriptModule.InputOutput]
      modules.update(
        0, old.copy(imports = old.imports ++ Map(name -> artType))
      )
    } else
    {
      modules.insert(0, VizierScriptModule.InputOutput(
        id = nextIOId,
        imports = Map(name -> artType),
        exports = Map.empty
      ))
    }
  }

  def addStaticOutput(name: String)
  {
    activeOutputs() = activeOutputs.now + name
  }

  def removeStaticOutput(name: String)
  {
    activeOutputs() = activeOutputs.now - name
  }

  def toggleStaticOutput(name: String)
  {
    if(activeOutputs.now(name)){
      activeOutputs() = activeOutputs.now - name
    } else {
      activeOutputs() = activeOutputs.now + name
    }
  }

  def getModules: Seq[VizierScriptModule] = 
  {
    val exports:Map[String, ArtifactType.T] = 
      activeOutputs.now.toSeq.flatMap { o => outputs.now.get(o).map { o -> _ } }.toMap

    modules.toSeq ++ (
      if(exports.isEmpty) { Seq() }
      else { Seq(
        VizierScriptModule.InputOutput(
          id = nextIOId,
          imports = Map.empty,
          exports = exports.toMap
        )
      ) }
    )
  }

  def toScript: VizierScript =
    script.copy(
      version = script.version + 1,
      name = scriptName,
      modules = getModules
    )

  def scriptName = nameInput.value

  def save(): Unit =
  {
    if(script.id < 0){
      Vizier.api.scriptCreateScript(
        name = scriptName,
        projectId = script.projectId.toString,
        branchId = script.branchId.toString,
        workflowId = script.workflowId.toString,
        modules = getModules
      )
    } else {
      Vizier.api.scriptUpdateScript(
        scriptId = script.id.toString,
        name = scriptName,
        projectId = script.projectId.toString,
        branchId = script.branchId.toString,
        workflowId = script.workflowId.toString,
        modules = getModules
      )
    }
  }

  val nameInput =
    input(
      `type` := "text", 
      id := "script_name", 
      name := "script_name", 
      value := script.name
    ).render

  val moduleView = 
    RxBufferView(div().render, 
      modules
        .rxMap { 
          case module: VizierScriptModule.Inline =>
            val enabledClass = if(module.enabled){ "enabled" } else { "disabled" }
            val commandClass = s"module_${module.spec.command.packageId}_${module.spec.command.commandId}"
            div(`class` := s"module $enabledClass $commandClass",
              h3(
                button(
                  if(module.enabled) { "Disable" } else { "Enable" },
                  onclick := { _:dom.Event => toggleEnabled(module.id) }
                ),
                `class` := "module_type", s"${module.spec.command.packageId}.${module.spec.command.commandId}",
                if(module.enabled){ span("") }
                else { span(" [DISABLED]") }
              ),
              pre(`class` := "module_content", module.spec.text)
            ).render
          case module: VizierScriptModule.InputOutput =>
            val enabledClass = if(module.enabled){ "enabled" } else { "disabled" }
            div(
              `class` := s"module $enabledClass script_in_out",
              h3("Input/Output"),
              ul(
                module.imports.map { case (label, artType) =>
                  li("➡: ", label, " [", artType.toString, "]") 
                }.toSeq,
                module.exports.map { case (label, artType) =>
                  li("🠘: ", label, " [", artType.toString, "]") 
                }.toSeq,
              ),
            ).render
        }
    )

  val root = div(`class` := "script_editor",
    div(`class` := "script_name",
      label(`for` := "script_name", "Name:"),
      nameInput,
    ),
    div(button("Save Workflow", onclick := { _:dom.Event => save() })),
    Rx {
      if(inputs().isEmpty){ div() }
      else {
        div(h2("Missing Inputs"),
          ul(
            inputs().toSeq.sortBy { _._1 }.map { case (name, artType) =>
              li(name, " [", artType.toString, "]",
                button("Add Import",
                  onclick := { _:dom.Event => 
                    addStaticInput(name, artType)
                  }
                )
              ) 
            }
          )
        )
      }
    }.reactive,
    div(
      h2("Script"),
      moduleView.root
    ),
    div(h2("Available Outputs"),
      Rx {
        val active = activeOutputs()
        ul(
          outputs().toSeq.sortBy { _._1 }.map { case (label, artType) =>
            li(
              label, " [", artType.toString, 
              (if(active(label)) { "; ACTIVE" } else { "" }),
              "] ",
              button(
                if(active(label)) { "Deactivate" } else { "Activate" },
                onclick := { _:dom.Event => toggleStaticOutput(label) }
              )
            )
          }
        )
      }.reactive
    ),
  ).render
}

object ScriptEditor
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectId = arguments.get("project").map { _.toLong }
    val scriptId = arguments.get("script").map { _.toLong }
    val branchId = arguments.get("branch").map { _.toLong }
    val workflowId = arguments.get("workflow").map { _.toLong }

    val editor = Var[Option[ScriptEditor]](None)

    var loading: Future[ScriptEditor] = 
      (scriptId, projectId, branchId, workflowId) match {
        case (_, Some(projectId), Some(branchId), Some(workflowId)) => 
          Vizier.api.workflowGet(projectId, branchId, workflowId)
                .map { workflow => 
                  new ScriptEditor(VizierScript.fromWorkflow(projectId, branchId, workflow))
                }
        case (Some(scriptId), _, _, _) =>
          Vizier.api.scriptGetScriptById(scriptId.toString)
                .map { new ScriptEditor(_) }
        case _ => 
          Future.failed(new Exception("No script provided"))
      }

    loading.onSuccess { case ed => editor() = Some(ed) }

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      loading.onFailure { case err => Vizier.error(err.getMessage) }
      val root = div(`class` := "script_editor", 
                      editor.map { 
                        _.map { _.root }
                         .getOrElse { Spinner(50) }
                      }.reactive
                    ).render

      document.body.appendChild(root)
      OnMount.trigger(document.body)
    })
  }
}