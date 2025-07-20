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
package info.vizierdb.ui.components.editors

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.ui.components.ModuleSummary
import info.vizierdb.ui.components.Module
import info.vizierdb.ui.components.CodeParameter
import rx._
import info.vizierdb.ui.facades.{CodeMirrorEditor, CodeMirrorChangeObject}
import info.vizierdb.ui.components.ModuleEditorDelegate
import info.vizierdb.serialized.PackageCommand
import info.vizierdb.ui.components.ModuleEditor
import info.vizierdb.serialized.CommandArgument
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.components.Parameter
import info.vizierdb.serialized.ParameterDescriptionTree
import info.vizierdb.serialized.CodeParameterDescription
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.components.EnvironmentParameter
import info.vizierdb.serialized.SimpleParameterDescription
import info.vizierdb.ui.components.StringParameter
import info.vizierdb.ui.components.BooleanParameter

class CodeModuleSummary(
  module: Module,
  language: String
)(implicit owner: Ctx.Owner) extends ModuleSummary
{
  // Use a simple code parameter as the editor
  val code  = new CodeParameter(
    id = "code", 
    name = language.capitalize,
    language = language,
    required = true,
    hidden = false,
    startWithSnippetsHidden = true
  )

  // Update the code parameter when things change
  // TODO: It could be pretty disconcerting if the user is editing and 
  //       another user/etc... changes the underlying cell on them.  We
  //       should probably default to disabling the following trigger
  //       when in editing mode.
  module.subscription.text.trigger { code.set(_) }

  // We want the editor to let us know when the user tries to edit anything.
  // This can't happen until the editor appears in the DOM, so we register
  // a callback with the parameter.
  code.onInit = { editor =>
    editor.on("beforeChange", { (instance: Any, changeObj: Any) => 
      if(!editing){ startEditing() }
    })
  }

  // Logic for managing the editor.
  var editing = false
  def startEditing(): Unit = 
  {
    // This triggers a series of changes that should invoke editor() below
    module.openEditor()
  }

  override def editor(packageId: String, command: PackageCommand, delegate: ModuleEditorDelegate)(implicit owner: Ctx.Owner): ModuleEditor = 
  {
    editing = true
    code.hideSnippets() = false
    new CodeModuleEditor(packageId, command, delegate)
  }

  override def endEditor(): Unit =
  {
    // the editing lifecycle typically replaces the module if the edit is 
    // successful.  The only reasonEndEditor will be called is if the 
    // user hits the 'back' button.  If so, reset the state.

    // Since the codemirror node was moved to a different parent (in the 
    // editor), we need to manually re-insert it into the dom.
    while(rootNode.hasChildNodes()){
      rootNode.removeChild(rootNode.firstChild)
    }
    rootNode.appendChild(code.root)

    // Revert the state of the code
    code.set(module.subscription.text.now:String)
    // the 'set' above is going to trigger an 'edit', so set the following 
    // state variables last
    code.hideSnippets() = true
    editing = false
  }

  class CodeModuleEditor(val packageId: String, command: PackageCommand, val delegate: ModuleEditorDelegate)
    extends ModuleEditor
  {
    val codeField = 
      command.parameters.find { p =>
        p.isInstanceOf[CodeParameterDescription] && (p.datatype != "environment")
      }.map { _.id }.getOrElse { "code" }

    val environmentParam: Option[EnvironmentParameter] = 
      command.parameters.collectFirst { 
        case p:CodeParameterDescription if p.datatype == "environment" =>
          new EnvironmentParameter(p)
      }

    val outputDatasetField: Option[StringParameter] =
      command.parameters.collectFirst {
        case p:SimpleParameterDescription if p.id == "output_dataset" =>
          new StringParameter(p)
      }

    val showOutputField: Option[BooleanParameter] =
      command.parameters.collectFirst {
        case p:SimpleParameterDescription if p.id == "show_output" =>
          new BooleanParameter(p)
      }

    // The above is a HUGE cludge.  
    // Why are we manually allowing *specific* fields?
    // TODO: Fix before 2.1
    // https://github.com/VizierDB/vizier-scala/issues/311

    var otherArgs = Seq[CommandArgument]()

    def commandId = command.id
    def currentState = 
      otherArgs ++ Seq(
        CommandArgument(codeField, code.value)
      ) ++ environmentParam.map { e =>
        CommandArgument(e.id, e.value)
      } ++ outputDatasetField.map { o =>
        CommandArgument(o.id, o.value)
      } ++ showOutputField.map { o =>
        CommandArgument(o.id, o.value)
      }

    def loadState(arguments: Seq[CommandArgument]): Unit =
    {
      // we should have already "loaded" the code itself.
      // Same goes for the environment parameter
      otherArgs = arguments.filter { a => 
        if(a.id == codeField){ false }
        else if(environmentParam.isDefined && environmentParam.get.id == a.id){ false }
        else if(outputDatasetField.isDefined && outputDatasetField.get.id == a.id){ false }
        else { true }
      }

      if(environmentParam.isDefined){
        val arg = arguments.find { _.id == environmentParam.get.id }
        if(arg.isDefined){
          environmentParam.get.set(arg.get.value)
        }
      }

      if(outputDatasetField.isDefined){
        val arg = arguments.find { _.id == outputDatasetField.get.id }
        if(arg.isDefined){
          outputDatasetField.get.set(arg.get.value)
        }
      }

      if(showOutputField.isDefined){
        val arg = arguments.find { _.id == showOutputField.get.id }
        if(arg.isDefined){
          showOutputField.get.set(arg.get.value)
        }
      }
    }

    val editorFields: Frag = div(
      OnMount { node =>
        code.editor.focus()
      },
      code.root,
      environmentParam.map { _.root },
      outputDatasetField.map { _.root },
      showOutputField.map { _.root },
    ).render
  }

  // The display logic
  val rootNode: dom.html.Div = div(
    code.root
  ).render

  val root = div(rootNode)
}