package info.vizierdb.ui.components.editors

import info.vizierdb.ui.components._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized.FilesystemObject
import info.vizierdb.types.MIME
import info.vizierdb.ui.Vizier
import info.vizierdb.types.DatasetFormat
import play.api.libs.json._
import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
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
  ArtifactDescription,
  DatasetColumn,
  PackageCommand
}
import info.vizierdb.types._
import info.vizierdb.nativeTypes.JsValue
import scala.util.{ Success, Failure }
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.network.BranchWatcherAPIProxy
import info.vizierdb.ui.widgets.FontAwesome
import java.awt.Font



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
    for( arg <- arguments ){   
      arg.id match {
        case "dataset" => dataset.set(arg.value)
        case "xcol" => xcol.set(arg.value)
        case "ycol" => ycol.set(arg.value)
        case "yList" => yListParam.set(arg.value)
      }
    }
  }

    override def currentState: Seq[CommandArgument] =
      {
        println(dataset.toArgument)
        println(datasetProfile)
        Seq(
          dataset.toArgument,
          xcol.toArgument,
          yListParam.toArgument,
        )
    }
  

  val dataset = 
    new ArtifactParameter(
      id = "dataset",
      name = "Dataset",
      artifactType = ArtifactType.DATASET,
      artifacts = delegate.visibleArtifacts
                          .map { _.mapValues { _._1.t } },
      required = true,
      hidden = false,
    )
  
  val xcol = 
    new ColIdParameter(
      "xcol",
      "X-axis",
      Rx{
          dataset.selectedDataset() match {
            case None => Seq.empty
            case Some(datasetId) => 
              delegate.visibleArtifacts().get(datasetId) match {
                case Some((ds:DatasetSummary,_)) => ds.columns
                case Some((ds:DatasetDescription,_)) => ds.columns
                case None => Seq.empty
              }
          }
      },
      true,
      false,
    )

  val ycol =
    new ColIdParameter(
      "ycol",
      "Y-Axis",
      Rx{
          dataset.selectedDataset() match {
            case None => Seq.empty
            case Some(datasetId) => 
              delegate.visibleArtifacts().get(datasetId) match {
                case Some((ds:DatasetSummary,_)) => ds.columns
                case Some((ds:DatasetDescription,_)) => ds.columns
                case None => Seq.empty
              
              }
          }
      },
      true,
      false,
    )

  val yListCol = 
    new ColIdListParameter(
      "yList",
      "Y-axes",
      true,
      Seq("y"),
      {
        () => 
          Seq(
            ycol
          ),
      },
      false,
    )
  
  val yListParam = 
    new ListParameter(
      "yList",
      "Y-axes",
      Seq[String]("ycol"),
      {
        () => 
          Seq(
            ycol
          ),
      },
      true,
      false
    )


  //Have: 
  //VisibleArtifacts = Rx[Map[String, (serialized.ArtifactSummary, WorkflowElement)]]
  //selectedDataset = Rx[Option[String]]

  // val colIdDataset = delegate.visibleArtifacts.now.get(selectedDataset.now.get)


  //Need this Rx[Seq[serialized.DatasetColumn]]
  val listParam_bar: ListParameter =
    new ListParameter("series",
      "Bars",
      Seq[String]("dataset", "xcol", "yList"),
      {
        () => 
          Seq(
            dataset,
            xcol,
            yListParam
          ),
      },
      true,
      false
    )
  
  
  dataset.selectedDataset.triggerLater { _ match {
    case None => println("No dataset selected")
    case Some(ds) => Vizier.api.artifactGet(
      Vizier.project.now.get.projectId,
      delegate.visibleArtifacts.now.get(dataset.selectedDataset.now.get).get._1.id,
      None,
      None,
      Some("true")
    ).onComplete {
      case Success(artifactDescription) =>
        artifactDescription match {
          case ds:ArtifactDescription => 
            datasetProfile() = Some(ds)
          case _ => 
            Vizier.error("Not a dataset")
        }
      case Failure(exception) =>
        Vizier.error(exception.getMessage())
    }
  }}


  val datasetProfile: Var[Option[ArtifactDescription]] = Var(None)

  
  //What is displayed to users
  override val editorFields = 
    div(`class` := "bar_chart_editor",
      listParam_bar.root,
      div(`class` := "profiler",
      ),
        button(
          "Add Row",
          `class` := "add_row",
          onclick := { () => 
            println("Add Row")
          }
      ),
        button(
          FontAwesome("ellipsis-h"),
          `class` := "customization",
          onclick := { () => 
            println("Customize")
          }
        )
    )

      // div(`class` := "profiler",
      //   button (
      //     FontAwesome("ellipsis-h"),
      //     `class` := "chart_editor_button",
      //     onclick := { () => println(selectedDataset.now) }
      //   ),
      //   artifactResultContainer.render,
      // ),

}
