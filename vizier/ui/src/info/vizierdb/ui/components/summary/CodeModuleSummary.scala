package info.vizierdb.ui.components.summary

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

    val environmentParam = 
      command.parameters.collectFirst { 
        case p:CodeParameterDescription if p.datatype == "environment" =>
          new EnvironmentParameter(p)
      }

    var otherArgs = Seq[CommandArgument]()

    def commandId = command.id
    def currentState = 
      otherArgs ++ Seq(
        CommandArgument(codeField, code.value)
      ) ++ environmentParam.map { e =>
        CommandArgument(e.id, e.value)
      }

    def loadState(arguments: Seq[CommandArgument]): Unit =
    {
      // we should have already "loaded" the code itself.
      // Same goes for the environment parameter
      otherArgs = arguments.filter { a => 
        if(a.id == codeField){ false }
        if(environmentParam.isDefined && environmentParam.get.id == a.id){ false }
        else { true }
      }
    }

    val editorFields: Frag = div(
      OnMount { node =>
        code.editor.focus()
      },
      code.root,
      environmentParam.map { _.root }
    ).render
  }

  // The display logic
  val rootNode: dom.html.Div = div(
    code.root
  ).render

  val root = div(rootNode)
}