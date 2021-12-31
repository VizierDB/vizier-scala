package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import scala.scalajs.js
import info.vizierdb.serialized
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.util.Logging
import info.vizierdb.serializers._
import info.vizierdb.api.websocket
import info.vizierdb.types.ArtifactType
import info.vizierdb.serialized.{ 
  CommandArgument, 
  CommandArgumentList, 
  CommandDescription, 
  ParameterDescriptionTree,
  DatasetSummary,
  DatasetDescription,
  DatasetColumn

}
import info.vizierdb.nativeTypes.JsValue
import scala.util.{ Success, Failure }

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
          val response = 
            if(module.isLast){
               s.Client.workflowAppend(
                  packageId = packageId,
                  commandId = command.id,
                  arguments = arguments
                )
            } else {
              s.Client
                .workflowInsert(
                  modulePosition = module.position,
                  packageId = packageId,
                  commandId = command.id,
                  arguments = arguments
                )
            }
          response.onComplete { 
            case Success(workflow) =>
              logger.trace("SUCCESS!")
              if(workflow.actionModule.isDefined){
                logger.trace(s"has action module: ${workflow.actionModule}")
                module.id = workflow.actionModule
              } else {
                logger.debug(s"no action module... falling back: ${workflow.modules.size}")
                module.id = Some(workflow.modules(module.position).moduleId)
              }
              logger.debug(s"New module id is... ${module.id}")
            case f:Failure[_] =>
              logger.trace("REQUEST FAILED!")
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

  def setState(arguments: (String, JsValue)*) =
    loadState(CommandArgumentList(arguments:_*))

  val selectedDataset = Var[Option[String]](None)

  val parameters: Seq[Parameter] = 
    ParameterDescriptionTree(
      command.parameters.toSeq
    ).map { Parameter(_, this) }

  parameters.collect { 
    case dsParam:ArtifactParameter if dsParam.artifactType == ArtifactType.DATASET => dsParam 
  }.headOption match {
    case None => ()
    case Some(dsParameter) => 
      dsParameter.selectedDataset.trigger {
        selectedDataset() = dsParameter.selectedDataset.now
      }
  }

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