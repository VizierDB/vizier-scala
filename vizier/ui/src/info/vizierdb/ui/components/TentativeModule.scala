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

class TentativeModule(
  var position: Int, 
  val editList: TentativeEdits, 
  defaultPackageList: Option[Seq[serialized.PackageDescription]] = None
)(implicit owner: Ctx.Owner)
  extends ModuleEditorDelegate
{

  val activeView = Var[Option[Either[CommandList, ModuleEditor]]](None)
  val visibleArtifacts = Var[Rx[Map[String, serialized.ArtifactSummary]]](Var(Map.empty))
  val selectedDataset = Var[Option[String]](None)
  var id: Option[Identifier] = None
  def isLast = position >= editList.size - 1

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
    activeView() = Some(Right(new ModuleEditor(packageId, command, this)))
  }
  def cancelSelectCommand()
  {
    editList.dropTentative(this)
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
        activeView() = Some(Left(new CommandList(packages, this)))
      case None =>
        editList
          .project
          .api
          .packages
          .onSuccess { case packages => 
            activeView() = Some(Left(new CommandList(packages, this)))
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

  val root = li(
    // span(
    //   "Visible artifacts here: ",
    //   visibleArtifacts
    //     .flatMap { 
    //       _.map { _.keys.mkString(", ") }
    //     }
    //     .reactive
    // ),
    activeView.map {
      case None => b("Loading commands...")
      case Some(Left(commandList)) => commandList.root
      case Some(Right(editor)) => editor.root
    }.reactive
  )
}

class CommandList(
  packages: Seq[serialized.PackageDescription], 
  module: TentativeModule
){
  val root = 
    div(`class` := "module select-command", 
      div(
        `class` := "header",
        "New Cell",
        button("Cancel", 
          `class` := "cancel",
          onclick := { (e: dom.MouseEvent) => module.cancelSelectCommand() }
        )
      ),
      ul(
        `class` := "command_list",
        packages.map { pkg => 
          li(b(pkg.name), 
            div(
              pkg.commands.toSeq
                  .filterNot { _.hidden.getOrElse { false } }
                  .map { cmd => 
                    button(cmd.name, onclick := { 
                      (e: dom.MouseEvent) => module.selectCommand(pkg.id, cmd)
                    })
                  }
            )
          )
        }
      ),
    )
}

