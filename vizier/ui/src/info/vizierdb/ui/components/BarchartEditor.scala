package info.vizierdb.ui.components.editors

import info.vizierdb.ui.components._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized.FilesystemObject
import info.vizierdb.types.MIME
import info.vizierdb.ui.Vizier
import scala.util.Success
import scala.util.Failure
import info.vizierdb.util.Logging
import info.vizierdb.types.DatasetFormat
import play.api.libs.json._
import info.vizierdb.serializers._
import info.vizierdb.types._
import info.vizierdb.ui.components.EnumerableParameter
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
  DatasetColumn,
  PackageCommand
}
import info.vizierdb.types._
import info.vizierdb.nativeTypes.JsValue
import scala.util.{ Success, Failure }
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.network.BranchWatcherAPIProxy
import info.vizierdb.ui.components.editors._
import info.vizierdb.ui.widgets.FontAwesome



class BarchartEditor(
  override val delegate: ModuleEditorDelegate,
  override val packageId: String,
  override val command: serialized.PackageCommand
)(implicit owner: Ctx.Owner)
  extends DefaultModuleEditor(packageId, command, delegate)
  with Logging
{

  //Keeps State of the arguments after editing
  override def loadState(arguments: Seq[CommandArgument]): Unit = 
  {
    println("loadState")
    // for(arg <- arguments){
    //   getParameter.get(arg.id) match {
    //     case Some(parameter) => parameter.set(arg.value)
    //     case None => logger.warn(s"Unknown argument: ${arg.id}")
    //   }
    // }
  }

    override def currentState: Seq[CommandArgument] =
      {
      Seq(CommandArgument(
        id = "dataset",
        value = JsString(selectedDataset.now.getOrElse { "" })
      ))
    }
  

  val artifactResultContainer = div()
  
  def profiler = 
    Vizier.api.artifactGet(
      Vizier.project.now.get.projectId,
      delegate.visibleArtifacts.now.get(selectedDataset.now.get).get._1.id,
      None,
      None,
      Some("true")
    ).onComplete {
      case Success(artifactDescription) =>
        artifactResultContainer.appendChild(div(artifactDescription.toString).render)
      case Failure(exception) =>
        artifactResultContainer.appendChild(div(s"Error: ${exception.getMessage}").render)
    }
    
  //What is displayed to users
  override val editorFields = 
    div(`class` := "bar_chart_editor",
      div(`class` := "dataset_selector",
        button (
          FontAwesome("ellipsis-h"),
          `class` := "chart_editor_button",
          onclick := { () => println("functionality") }
        )
      ),
      div(`class` := "parameters",
        parameters.filter { !_.hidden }
                  .map { param => div(width := "100%", param.root) }
      ),
      div(`class` := "profiler",
        button (
          FontAwesome("ellipsis-h"),
          `class` := "chart_editor_button",
          onclick := { () => println(selectedDataset.now) }
        ),
        artifactResultContainer.render,
      ),

    )
}
