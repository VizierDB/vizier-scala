package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import info.vizierdb.ui.API
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.types.ArtifactType
import info.vizierdb.encoding
import scala.concurrent.{ Future, Promise }
import info.vizierdb.types.Identifier

class TentativeModule(
  var position: Int, 
  val editList: TentativeEdits, 
  defaultPackageList: Option[Seq[encoding.PackageDescriptor]] = None
)(implicit owner: Ctx.Owner)
{

  val activeView = Var[Option[Either[CommandList, ModuleEditor]]](None)
  val visibleArtifacts = Var[Rx[Map[String, Artifact]]](Var(Map.empty))
  val selectedDataset = Var[Option[String]](None)
  var id: Option[Identifier] = None

  loadPackages()

  def selectCommand(packageId: String, command: encoding.CommandDescriptor)
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

  def nextModule(): Option[Identifier] =
  {
    editList.elements
            .drop(position)
            .find { _.isLeft }
            .collect { case Left(m) => m.id }
  }

  val root = li(
    span(
      "Visible artifacts here: ",
      Rx { 
        val a = visibleArtifacts()
        Rx { 
          a().keys.mkString(", ")
        }
      }
    ),
    Rx { 
      activeView.map {
        case None => b("Loading commands...")
        case Some(Left(commandList)) => commandList.root
        case Some(Right(editor)) => editor.root
      }
    }
  )
}

class CommandList(
  packages: Seq[encoding.PackageDescriptor], 
  module: TentativeModule
){
  val root = 
    div(`class` := "module select-command", 
      "Create a command... ",
      ul(
        packages.map { pkg => 
          li(b(pkg.name), 
            div(
              pkg.commands.toSeq.map { cmd => 
                button(cmd.name, onclick := { 
                  (e: dom.MouseEvent) => module.selectCommand(pkg.id, cmd)
                })
              }
            )
          )
        }
      ),
      div(
        button("Cancel", onclick := { (e: dom.MouseEvent) => module.cancelSelectCommand() })
      )
    )
}

