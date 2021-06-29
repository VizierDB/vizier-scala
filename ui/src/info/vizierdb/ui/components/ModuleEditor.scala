package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import scala.scalajs.js
import info.vizierdb.ui.network.{ CommandDescriptor, CommandArgument, ModuleCommand }
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.util.Logging


class ModuleEditor(
  val packageId: String, 
  val command: CommandDescriptor, 
  val module: TentativeModule
)(implicit owner: Ctx.Owner) 
  extends Object 
  with Logging
{

  def saveState()
  {
    module
      .editList
      .project
      .branchSubscription match {
        case None => logger.error("No connection!")
        case Some(s) => 
          s.allocateModule(
            command = serialized,
            atPosition = if(module.nextModule.isDefined){ Some(module.position) } else { None }
          )
          .onSuccess { case id =>
            module.id = Some(id)
          }
      }
  }

  def loadState(arguments: Seq[CommandArgument])
  {
    for(arg <- arguments){
      getParameter.get(arg.id) match {
        case Some(parameter) => parameter.set(arg.value)
        case None => logger.warn(s"Load state with undefined parameter: ${arg.id}")
      }
    }
  }

  def setState(arguments: (String, Any)*)
  {
    loadState(
      arguments.map { case (id, value) =>
        assert(value.asInstanceOf[js.UndefOr[Any]].isDefined)
        js.Dictionary(
          "id" -> id,
          "value" -> value
        ).asInstanceOf[CommandArgument]
      }
    )
  }

  val parameters: Seq[Parameter] = 
    Parameter.collapse(
      command.parameters.toSeq
    ).map { Parameter(_, this) }
  lazy val getParameter:Map[String, Parameter] = 
    parameters.map { p => p.id -> p }.toMap

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