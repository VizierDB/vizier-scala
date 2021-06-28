package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import scala.scalajs.js
import info.vizierdb.ui.network.{ CommandDescriptor, CommandArgument, ModuleCommand }

class ModuleEditor(
  val packageId: String, 
  val command: CommandDescriptor, 
  val module: TentativeModule
)(implicit owner: Ctx.Owner) {

  def saveState()
  {
    module
      .editList
      .project
      .branchSubscription match {
        case None => println("ERROR: No connection!")
        case Some(s) => 
          s.allocateModule(
            command = serialized,
            atPosition = if(module.nextModule.isDefined){ Some(module.position) } else { None }
          )
      }
    // println(s"Would Save: $packageId.${command.id}(${arguments.map { x => x.toString() }.mkString(", ")})")
  }

  val parameters: Seq[Parameter] = 
    Parameter.collapse(
      command.parameters.toSeq
    ).map { Parameter(_, this) }

  def arguments: Seq[CommandArgument] =
    parameters.map { _.toArgument }.toSeq

  def serialized: ModuleCommand =
  {
    val me = this
    js.Dynamic.literal(
      packageId = me.packageId,
      commandId = me.command.id,
      arguments = me.arguments
    ).asInstanceOf[ModuleCommand]
  }


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