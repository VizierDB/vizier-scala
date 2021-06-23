package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import info.vizierdb.ui.network.{ CommandDescriptor, ModuleArgument }


class ModuleEditor(
  val packageId: String, 
  val command: CommandDescriptor, 
  val module: TentativeModule
)(implicit owner: Ctx.Owner) {

  def saveState()
  {
    println(s"Would Save: $packageId.${command.id}(${arguments.map { x => x.toString() }.mkString(", ")})")
  }

  val parameters: Seq[Parameter] = 
    Parameter.collapse(
      command.parameters.toSeq
    ).map { Parameter(_, this) }

  def arguments: Seq[ModuleArgument] =
    parameters.map { _.toArgument }.toSeq

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