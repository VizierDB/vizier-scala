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
import info.vizierdb.types._
import info.vizierdb.nativeTypes.JsValue
import scala.util.{ Success, Failure }
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.network.BranchWatcherAPIProxy

class ModuleEditor(
  val packageId: String, 
  val command: serialized.PackageCommand, 
  val delegate: ModuleEditorDelegate
)(implicit owner: Ctx.Owner) 
  extends Object 
  with Logging
{

  def saveState()
  {
    val response = 
      if(delegate.realModuleId.isDefined) {
        delegate.client.workflowReplace(
          modulePosition = delegate.position,
          packageId = packageId,
          commandId = command.id,
          arguments = arguments
        )
      } else if(delegate.isLast){
        delegate.client.workflowAppend(
          packageId = packageId,
          commandId = command.id,
          arguments = arguments
        )
      } else {
        delegate.client.workflowInsert(
          modulePosition = delegate.position,
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
          delegate.setTentativeModuleId(workflow.actionModule.get)
        } else {
          logger.debug(s"no action module... falling back: ${workflow.modules.size}")
          delegate.setTentativeModuleId(workflow.modules(delegate.position).moduleId)
        }
        logger.debug(s"New module id is... ${delegate.tentativeModuleId}")
      case f:Failure[_] =>
        logger.trace("REQUEST FAILED!")
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
        button("Back", onclick := { (e: dom.MouseEvent) => delegate.cancelEditor() }),
        button("Save", onclick := { (e: dom.MouseEvent) => saveState() })
      )
    )
}

trait ModuleEditorDelegate
{
  def client: BranchWatcherAPIProxy
  def cancelEditor(): Unit
  def realModuleId: Option[Identifier]
  def tentativeModuleId: Option[Identifier]
  def setTentativeModuleId(newId: Identifier): Unit
  def position: Int
  def isLast: Boolean
  def visibleArtifacts: Var[Rx[Map[String, serialized.ArtifactSummary]]]
}