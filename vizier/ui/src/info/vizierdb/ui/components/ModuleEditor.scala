package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import scala.scalajs.js
import info.vizierdb.serialized
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.util.Logging
import autowire._
import info.vizierdb.serializers._
import info.vizierdb.api.websocket
import info.vizierdb.serialized.{ CommandArgument, CommandDescription, ParameterDescriptionTree }

class ModuleEditor(
  val packageId: String, 
  val command: serialized.PackageCommand, 
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
          s.Client[websocket.BranchWatcherAPI]
            .workflowInsert(
              modulePosition = module.position,
              packageId = packageId,
              commandId = command.id,
              arguments = arguments
            )
            .call()
            .onSuccess { case workflow =>
              module.id = Some(workflow.modules(module.position).moduleId)
            }

          // s.requests.(
          //   command = serialized,
          //   atPosition = if(module.nextModule.isDefined){ Some(module.position) } else { None }
          // )
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
    ParameterDescriptionTree(
      command.parameters.toSeq
    ).map { Parameter(_, this) }
  lazy val getParameter:Map[String, Parameter] = 
    parameters.map { p => p.id -> p }.toMap

  def arguments: Seq[CommandArgument] =
    parameters.map { _.toArgument }

  def serialized: CommandDescription =
    CommandDescription(
      packageId = packageId,
      commandId = command.id,
      arguments = arguments
    )


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