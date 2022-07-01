package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.types.ArtifactType
import info.vizierdb.serialized
import scala.concurrent.{ Future, Promise }
import info.vizierdb.types.Identifier
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.util.Trie
import info.vizierdb.ui.widgets.ScrollIntoView

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

class CommandList(
  packages: Seq[serialized.PackageDescription], 
  module: TentativeModule
)(implicit owner:Ctx.Owner){

  val keywords = 
    Trie.ofSeq[String](
      packages.flatMap { pkg =>
        val packageKeywords = pkg.name.toLowerCase.split("[^a-zA-Z]") :+ pkg.id
        pkg.commands.toSeq
           .filterNot { _.hidden.getOrElse { false } }
           .flatMap { cmd => 
              val commandKeywords =
                packageKeywords ++
                  cmd.name.toLowerCase.split(" +") :+ 
                  cmd.id
              val commandKey = s"${pkg.id}.${cmd.id}"
              commandKeywords.map { _ -> commandKey }
           }
      }
    )

  val searchField =
    input(
      placeholder := "Search modules...",
      onkeydown := { _:dom.Event =>  
                        dom.window.requestAnimationFrame { _ => refreshSelectedCommands() } }
    ).render

  val selectedCommands = Var[Set[String]](Set.empty)

  def refreshSelectedCommands():Unit =
  {
    val term = searchField.value:String
    if(term.isEmpty()){ selectedCommands() = Set[String]() }
    else {
      selectedCommands() = keywords.prefixMatch(term)
    }
  }

  val root = 
    div(`class` := "select_command", 
      div(`class` := "command_search",
        FontAwesome("search"),
        searchField,
        Rx { 
          if(selectedCommands().isEmpty) {
            button(
              FontAwesome("ban"),
              visibility := "hidden"
            )
          } else {
            button(
              FontAwesome("ban"),
              onclick := { _:dom.Event =>
                searchField.value = ""
                dom.window.requestAnimationFrame( _ => refreshSelectedCommands() )
              }
            )
          }
        }.reactive
      ),
      Rx {
        val activeSelection = selectedCommands()
        ul(
          `class` := "command_list",
          packages.map { pkg => 
            li(b(pkg.name), 
              div(
                pkg.commands.toSeq
                    .filterNot { _.hidden.getOrElse { false } }
                    .map { cmd => 
                      val isSuggested = 
                        if(activeSelection.isEmpty){
                          cmd.suggest.getOrElse(false)
                        } else {
                          activeSelection(s"${pkg.id}.${cmd.id}")
                        }
                      button(
                        cmd.name, 
                        `class` := s"command${if(isSuggested){ " suggested" } else { "" }}",
                        onclick := { 
                          (e: dom.MouseEvent) => module.selectCommand(pkg.id, cmd)
                        })
                    }
              )
            )
          },
        )
      }.reactive,
      div(`class` := "editor_actions",
        button(
          FontAwesome("ban"),
          " Cancel", 
          `class` := "cancel",
          onclick := { (e: dom.MouseEvent) => module.cancelSelectCommand() }
        ).render
      )
    )

  def simulateClick(packageId: String, commandId: String) =
  {
    packages.find { _.id == packageId } match {
      case Some(pkg) => 
        pkg.commands.find { _.id == commandId } match {
          case Some(cmd) => module.selectCommand(packageId, cmd)
          case None => println(s"SIMULATE CLICK ON TENTATIVE MODULE FAILED: NO COMMAND $commandId")
        }
      case None => println(s"SIMULATE CLICK ON TENTATIVE MODULE FAILED: NO PACKAGE $packageId")
    }
  }
}

