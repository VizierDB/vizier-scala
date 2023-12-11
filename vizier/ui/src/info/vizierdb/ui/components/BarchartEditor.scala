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
  PackageCommand,
  PropertyList
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
  def xColumnData = Var[Option[Int]](None)
  //Keeps State of the arguments after editing
  override def loadState(arguments: Seq[CommandArgument]): Unit = 
  {
    for( arg <- arguments ){   
      arg.id match {
        case "series" => listParam_bar.set(arg.value)
        case "artifact" => artifact.set(arg.value)
      }
    }
  }

  override def currentState: Seq[CommandArgument] =
    {
      println(datasetProfile)
      Seq(
          listParam_bar.toArgument,
          artifact.toArgument,
      )
    }
  
  def dataset = 
    new ArtifactParameter(
      id = "dataset",
      name = "Dataset",
      artifactType = ArtifactType.DATASET,
      artifacts = delegate.visibleArtifacts
                          .map { _.mapValues { _._1.t } },
      required = true,
      hidden = false,
    )
  
  def xcol(dataset:ArtifactParameter) = 
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

  def ycol(dataset:ArtifactParameter) =
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
  
  def yListParam(dataset:ArtifactParameter) = 
    new ListParameter(
      "yList",
      "Y-axes",
      Seq[String]("ycol"),
      {
        () => 
          Seq(
            ycol(dataset)
          ),
      },
      true,
      false
    )
  

  
  def label =
    new StringParameter(
      "label",
      "Label",
      true,
      false,
      ""
    )

  def artifact = 
    new StringParameter(
      "artifact",
      "Output Artifact (blank to show only)",
      false,
      false,
      ""
    )

  //Have: 
  //VisibleArtifacts = Rx[Map[String, (serialized.ArtifactSummary, WorkflowElement)]]
  //selectedDataset = Rx[Option[String]]

  // val colIdDataset = delegate.visibleArtifacts.now.get(selectedDataset.now.get)


  //Need this Rx[Seq[serialized.DatasetColumn]]
  val listParam_bar: ListParameter =
    new ListParameter("series",
      "Bars",
      Seq[String]("Dataset", "X", "Y Params", "Filter", "Label"),
      {
        () => 
          val currentDataset = dataset
          val xCol = xcol(currentDataset)
          val newFilter = filter
          profiler(currentDataset)
          xColChange(xCol)
          Seq(
            currentDataset,
            xCol,
            yListParam(currentDataset),
            newFilter,
            label
          ),
      },
      true,
      false
    )

    def filter =
      new NumericalFilterParameter(
        "filter",
        "Filter",
        datasetProfile,
        xColumnData,
        false,
        false,
    )
  
  def profiler(dataset:ArtifactParameter) =  dataset.selectedDataset.trigger { _ match {
    case None => println("No dataset selected")
    case Some(ds) => Vizier.api.artifactGet(
      Vizier.project.now.get.projectId,
      delegate.visibleArtifacts.now.get(dataset.selectedDataset.now.get).get._1.id,
      limit = Some(0),
      profile = Some("true")
    ).onComplete {
      case Success(artifactDescription) =>
        artifactDescription match {
          case ds:DatasetDescription => 
            filter.updateProfileData(ds.properties)
          case _ => 
            Vizier.error("Not a dataset")
        }
      case Failure(exception) =>
        Vizier.error(exception.getMessage())
    }
  }}

  def xColChange(currentXCol:ColIdParameter) = currentXCol.selectedColumn.trigger { _ match {
    case None => println("No column selected")
    case Some(col) => filter.updateXColumnData(datasetProfile.now, Some(col))
  }}

  def yColChange(currentYCol:ColIdParameter) = currentYCol.selectedColumn.trigger { _ match {
    case None => println("No column selected")
    case Some(col) => selectedYCol() = selectedYCol.now :+ col
  }}

  
  
  val datasetProfile: Var[Option[PropertyList.T]] = Var(None)

  val selectedXCol = Var[Option[Int]](None)
  val selectedYCol = Var[Seq[Int]](Seq.empty)

  
  //What is displayed to users
  override val editorFields = 
    div(`class` := "bar_chart_editor",
      listParam_bar.root,
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
