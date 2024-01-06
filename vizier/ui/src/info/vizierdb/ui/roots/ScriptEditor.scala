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
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Tooltip
import scala.util.{ Success, Failure }
import info.vizierdb.ui.widgets.BrowserLocation

class ScriptEditor(script: VizierScript)(implicit owner: Ctx.Owner)
{

  val modules: RxBufferVar[VizierScriptModule] = 
    RxBuffer.ofSeq(script.modules)

  var activeOutputs = Var[Set[String]](Set.empty)

  var scriptId = script.id
  val scriptName = Var[String](script.name)
  val scriptVersion = Var[String](script.version.toString)

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

  def removeInput(name: String, moduleId: Identifier)
  {
    for( (module, idx) <- modules.zipWithIndex )
    {
      module match {
        case module: VizierScriptModule.InputOutput if module.id == moduleId =>
          modules.update(
            idx, module.copy(imports = module.imports - name)
          )
        case _ => ()
      }
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
      name = nameInput.value,
      modules = getModules
    )

  val savingStatus = Var[Option[dom.Node]](None)

  def save(): Unit =
  {
    savingStatus() = Some(Spinner(8).render)
    // There's some sort of dumb GC that triggers if `ret` is not a variable.  
    // Do not try to inline it and the subsequent onComplete.
    val ret: Future[VizierScript] = 
      if(script.id < 0){
        Vizier.api.scriptCreateScript(
          name = nameInput.value,
          projectId = script.projectId.toString,
          branchId = script.branchId.toString,
          workflowId = script.workflowId.toString,
          modules = getModules
        )
      } else {
        Vizier.api.scriptUpdateScript(
          scriptId = script.id.toString,
          name = nameInput.value,
          projectId = script.projectId.toString,
          branchId = script.branchId.toString,
          workflowId = script.workflowId.toString,
          modules = getModules
        )
      }
    ret.onComplete { 
      case Success(newScript) => 
        scriptId = newScript.id
        scriptName() = newScript.name
        scriptVersion() = newScript.version.toString
        savingStatus() = Some(FontAwesome("check").render)
        dom.window.setTimeout( { () => savingStatus() = None }, 2000 )
        BrowserLocation.replaceQuery("script" -> scriptId.toString)

      case Failure(err) => 
        savingStatus() = Some(FontAwesome("times").render)
        dom.window.setTimeout( { () => savingStatus() = None }, 2000 )
        Vizier.error(err.getMessage())
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
    RxBufferView(div(`class` := "script_content").render, 
      modules
        .rxMap { 

          /////////////// Inline Module //////////////////////

          case module: VizierScriptModule.Inline =>
            val enabledClass = if(module.enabled){ "enabled" } else { "disabled" }
            val commandClass = s"module_${module.spec.command.packageId}_${module.spec.command.commandId}"
            div(`class` := s"module $enabledClass $commandClass",
              div(`class` := "menu",
                button(
                  FontAwesome("check-square-o"), 
                  onclick := { _:dom.MouseEvent => toggleEnabled(module.id) },
                  Tooltip("Toggle whether the cell will be included in the script")
                ),
              ),
              div(`class` := "module_body",
                h3(
                  `class` := "module_type", s"${module.spec.command.packageId}.${module.spec.command.commandId}",
                  if(module.enabled){ span("") }
                  else { span(" [DISABLED]") }
                ),
                pre(`class` := "module_content", module.spec.text)
              )
            ).render

          /////////////// Input/Output Module //////////////////////

          case module: VizierScriptModule.InputOutput =>
            val enabledClass = if(module.enabled){ "enabled" } else { "disabled" }
            div(
              `class` := s"module $enabledClass script_in_out",
              div(`class` := "menu"),
              div(`class` := "module_body",
                ( if(module.imports.isEmpty){ Seq[Frag]() }
                  else { Seq(
                    h3("Import Artifacts to Script"),
                    ul(
                      module.imports.map { case (label, artType) =>
                        li(FontAwesome(ArtifactType.icon(artType)), " ➡ ", label, 
                            button(FontAwesome("times"), onclick := { _:dom.Event => removeInput(label, module.id) })
                          ) 
                      }.toSeq,
                    )
                  )}
                ),
                ( if(module.exports.isEmpty){ Seq[Frag]() }
                  else { Seq(
                    h3("Export Artifacts from Script"),
                    ul(
                      module.exports.map { case (label, artType) =>
                        li("🠘: ", label, " [", artType.toString, "]") 
                      }.toSeq,
                    )
                  )}
                ),
              ),
            ).render
        }
    )

  val menu = 
    tag("nav")(id := "menu_bar",

      ////////////////// Logo ////////////////// 
      a(`class` := "left item", href := "index.html", img(src := "vizier.svg")),

      ////////////////// Logo ////////////////// 
      div(`class` := "left item", 
        Rx {
          div(`class` := "text", scriptName() + " (version ", scriptVersion(), ")")
        }.reactive
      )
    ).render

  val root = 
    div(`class` := "script_editor",
      menu,
      div(`class` := "content",
        div(`class` := "script_config",
          h2("Publish"),
          div(`class` := "script_field",
            label(`for` := "script_name", "Name:"),
            nameInput,
          ),
          div(`class` := "script_field",
            Rx {
              savingStatus() match {
                case None => 
                  div(button("Save Script", onclick := { _:dom.Event => save() }))
                case Some(s) =>
                  div(button(s))
              }
            }.reactive,
          ),
          Rx {
            if(inputs().isEmpty){ div() }
            else {
              div(
                h2("Missing Inputs"),
                ul(`class` := "inputs",
                  inputs().toSeq.sortBy { _._1 }.map { case (label, artType) =>
                    li(
                      FontAwesome(ArtifactType.icon(artType)),
                      label.take(20) + (if(label.size > 20){ "..." } else { "" }),
                      button("Add Import",
                        onclick := { _:dom.Event => 
                          addStaticInput(label, artType)
                        }
                      ),
                      Tooltip(label)
                    ) 
                  }
                )
              )
            }
          }.reactive,
          div(
            h2("Script Outputs"),
            p("(click to enable)"),
            Rx {
              val active = activeOutputs()
              ul(`class` := "outputs",
                outputs().toSeq.sortBy { _._1 }.map { case (label, artType) =>
                  li(`class` := (if(active(label)){ "enabled" } else { "disabled" }),
                    onclick := { _:dom.Event => toggleStaticOutput(label) },
                    FontAwesome(ArtifactType.icon(artType)),
                    label.take(20) + (if(label.size > 20){ "..." } else { "" }),
                    Tooltip(label + (if(active(label)){ " [Enabled]" } else { " [Disabled]" }))
                  )
                }
              )
            }.reactive
          ),
        ),
        div(`class` := "script_body",
          moduleView.root
        ),
      )
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
        case (_, Some(projectId), Some(branchId), None) => 
          Vizier.api.workflowHeadGet(projectId, branchId)
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