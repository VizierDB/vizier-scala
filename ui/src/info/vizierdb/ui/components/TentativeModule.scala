package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import rx._
import info.vizierdb.ui.network.PackageDescriptor
import info.vizierdb.ui.Vizier
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.ui.network.CommandDescriptor
import info.vizierdb.types.ArtifactType

class TentativeModule(var position: Int, editList: TentativeEdits)
                     (implicit owner: Ctx.Owner)
{

  val activeView = Var[Option[Either[CommandList, ModuleEditor]]](None)
  val visibleArtifacts = Var[Rx[Map[String, Artifact]]](Var(Map.empty))
  val selectedDataset = Var[Option[String]](None)

  loadPackages()

  def selectCommand(packageId: String, command: CommandDescriptor)
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
    Vizier.api.packages
              .onSuccess { case packages => 
                activeView() = Some(Left(new CommandList(packages, this)))
              }
  }

  val root = li(
    span(
      "Visible artifacts here: ",
      Rx { 
        val a = visibleArtifacts()
        // println(s"VISIBLE ARTIFACTS: $a")
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
  packages: Seq[PackageDescriptor], 
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

class ModuleEditor(
  val packageId: String, 
  val command: CommandDescriptor, 
  val module: TentativeModule
)(implicit owner: Ctx.Owner) {

  def saveState()
  {
    println(s"Would Save: $packageId.${command.id}")
  }

  val parameters: Seq[Parameter] = 
    Parameter.collapse(
      command.parameters.toSeq
    ).map { Parameter(_, this) }

  val root = 
    div(`class` := "module editable",
      h4(command.name),
      parameters.filter { !_.hidden }.map { param => div(param.root) },
      div(
        button("Cancel", onclick := { (e: dom.MouseEvent) => module.cancelEditor() }),
        button("Save", onclick := { (e: dom.MouseEvent) => saveState() })
      )
    )
}