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
import info.vizierdb.ui.rxExtras.{OnMount, RxBuffer, RxBufferView}
import info.vizierdb.types._
import info.vizierdb.nativeTypes.JsValue
import scala.util.{Success, Failure}
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.network.BranchWatcherAPIProxy
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.SideMenu
import java.awt.Font
import scala.concurrent.Future

class BarchartEditor(
    override val delegate: ModuleEditorDelegate,
    override val packageId: String,
    override val command: serialized.PackageCommand
)(implicit owner: Ctx.Owner)
    extends DefaultModuleEditor(packageId, command, delegate)
    with Logging {
  def xColumnData = Var[Option[Int]](None)
  val datasetProfile: Var[Option[PropertyList.T]] = Var(None)
  val selectedXCol = Var[Option[Int]](None)
  val selectedYCol = Var[Seq[Int]](Seq.empty)

  // Keeps State of the arguments after editing
  override def loadState(arguments: Seq[CommandArgument]): Unit = {
    for (arg <- arguments) {
      arg.id match {
        case "series"   => listParam_bar.set(arg.value)
        case "artifact" => artifact.set(arg.value)
      }
    }
  }

  override def currentState: Seq[CommandArgument] = {
    Seq(
      listParam_bar.toArgument,
      artifact.toArgument
    )
  }

  def makeDataset =
    new ArtifactParameter(
      id = "dataset",
      name = "Dataset",
      artifactType = ArtifactType.DATASET,
      artifacts = delegate.visibleArtifacts
        .map { _.mapValues { _._1.t } },
      required = true,
      hidden = false
    )

  def makeXColumn(dataset: ArtifactParameter) =
    new ColIdParameter(
      "xcol",
      "X-axis",
      Rx {
        dataset.selectedDataset() match {
          case None => Seq.empty
          case Some(datasetId) =>
            delegate.visibleArtifacts().get(datasetId) match {
              case Some((ds: DatasetSummary, _))     => ds.columns
              case Some((ds: DatasetDescription, _)) => ds.columns
              case None                              => Seq.empty
            }
        }
      },
      true,
      false
    )

  def makeYColumn(dataset: ArtifactParameter) =
    new ColIdParameter(
      "ycol",
      "Y-Axis",
      Rx {
        dataset.selectedDataset() match {
          case None => Seq.empty
          case Some(datasetId) =>
            delegate.visibleArtifacts().get(datasetId) match {
              case Some((ds: DatasetSummary, _))     => ds.columns
              case Some((ds: DatasetDescription, _)) => ds.columns
              case None                              => Seq.empty

            }
        }
      },
      true,
      false
    )

  def makeyListParam(dataset: ArtifactParameter) =
    new ListParameter(
      "yList",
      "Y-axes",
      Seq[String]("ycol"),
      { () =>
        Seq(
          makeYColumn(dataset)
        ),
      },
      true,
      false
    )

  def makeLabel =
    new StringParameter(
      "label",
      "Label",
      true,
      false,
      ""
    )

  def makeNewLabel =
    new ColorParameter(
      "color",
      "Color",
      true,
      false,
      None
    )

  def artifact =
    new StringParameter(
      "artifact",
      "Output Artifact (blank to show only)",
      false,
      false,
      ""
    )

  // Need this Rx[Seq[serialized.DatasetColumn]]
  val listParam_bar: ListParameter =
    new ListParameter(
      "series",
      "Bars",
      Seq[String]("Dataset", "X", "Y Params", "Filter", "Label", "Colors"),
      { () =>
        val currentDataset = makeDataset
        val xCol = makeXColumn(currentDataset)
        val newFilter = makeFilter(xCol)
        profiler(currentDataset, newFilter)
        xColChange(xCol, newFilter)
        Seq(
          currentDataset,
          xCol,
          makeyListParam(currentDataset),
          newFilter,
          makeLabel,
          makeNewLabel
        )
      },
      true,
      false
    )

  def makeFilter(xColumn: ColIdParameter) =
    new NumericalFilterParameter(
      "filter",
      "Filter",
      datasetProfile,
      xColumnData,
      Rx {
        xColumn.selectedColumn() match {
          case None      => 0
          case Some(col) => col
        }
      },
      false,
      false
    )

  def profiler(
      dataset: ArtifactParameter,
      filter: NumericalFilterParameter
  ): Future[Unit] = Future {
    dataset.selectedDataset.trigger {
      _ match {
        case None => println("No dataset selected")
        case Some(ds) =>
          Vizier.api
            .artifactGet(
              Vizier.project.now.get.projectId,
              delegate.visibleArtifacts.now
                .get(dataset.selectedDataset.now.get)
                .get
                ._1
                .id,
              limit = Some(0),
              profile = Some("true")
            )
            .onComplete {
              case Success(artifactDescription) =>
                artifactDescription match {
                  case ds: DatasetDescription =>
                    filter.profile_data() = Some(ds.properties)
                    println("Profiled ")
                  case _ =>
                    Vizier.error("Not a dataset")
                }
              case Failure(exception) =>
                Vizier.error(exception.getMessage())
            }
      }
    }
  }

  def xColChange(
      currentXCol: ColIdParameter,
      filter: NumericalFilterParameter
  ) = currentXCol.selectedColumn.trigger {
    _ match {
      case null => println("No column selected" + "null")
      case None =>
        println("No column selected" + currentXCol.selectedColumn.now)
      case Some(col) => filter.updateXColumnData(Some(col))
    }
  }

  def yColChange(currentYCol: ColIdParameter) =
    currentYCol.selectedColumn.trigger {
      _ match {
        case None      => println("No column selected")
        case Some(col) => selectedYCol() = selectedYCol.now :+ col
      }
    }

  val BarChart = new BarchartRow(
    makeDataset,
    makeXColumn(makeDataset),
    Seq(makeYColumn(makeDataset)),
    makeFilter(makeXColumn(makeDataset)),
    makeLabel,
    makeNewLabel
  )

  val SeqBarChart = Seq(
    BarChart.datatset,
    BarChart.xColumn,
    BarChart.yColumn,
    BarChart.filter,
    BarChart.label,
    BarChart.color
  )

  val titles = Seq("Dataset", "X", "Y Params", "Filter", "Label", "Colors")

  def renderBarChartRow(barChart: BarchartRow) = {
    Seq(
      td(barChart.datatset.root),
      td(barChart.xColumn.root),
      td(barChart.yColumn.head.root),
      td(barChart.filter.root),
      td(barChart.label.root),
      td(barChart.color.root)
    )
  }

  val barChartRoot =
    fieldset(
      legend("Bars"),
      table(
        `class` := "parameter_list",
        thead(
          tr(
            titles.map { th(_) },
            th("")
          )
        ),
        renderBarChartRow(BarChart)
      ),
      div(
        `class` := "side_menu",
        FontAwesome("ellipsis-v"),
        SideMenu.sideMenuElement
      )
    ).render

  // What is displayed to users
  override val editorFields =
    div(`class` := "bar_chart_editor", barChartRoot)
}

case class BarchartRow(
    datatset: ArtifactParameter,
    xColumn: ColIdParameter,
    yColumn: Seq[ColIdParameter],
    filter: NumericalFilterParameter,
    label: StringParameter,
    color: ColorParameter
)

case class BarChartConfig(rows: Seq[BarchartRow])
case class AppState(barchartConfig: BarChartConfig)

sealed trait BarchartAction
case class UpdateXColumn(rowIndex: Int, newXColumn: ColIdParameter)

object BarChartState {

  val appState = Var(AppState(BarChartConfig(Seq())))

  sealed trait BarchartAction
  case class UpdateFilter(
      rowIndex: Int,
      newFilter: NumericalFilterParameter
  ) extends BarchartAction
  case class UpdateColor(rowIndex: Int, newColor: ColorParameter)
      extends BarchartAction
  case class UpdateXColumn(rowIndex: Int, newXColumn: ColIdParameter)
      extends BarchartAction

  def barchartReducer(
      state: BarChartConfig,
      action: BarchartAction
  ): BarChartConfig = {
    action match {
      case UpdateXColumn(rowIndex, newXColumn) =>
        state.copy(
          rows = state.rows
            .updated(rowIndex, state.rows(rowIndex).copy(xColumn = newXColumn))
        )
      case UpdateFilter(rowIndex, newFilter) =>
        state.copy(
          rows = state.rows
            .updated(rowIndex, state.rows(rowIndex).copy(filter = newFilter))
        )
      case UpdateColor(rowIndex, newColor) =>
        state.copy(
          rows = state.rows
            .updated(rowIndex, state.rows(rowIndex).copy(color = newColor))
        )
      case _ => state
    }
  }

  def dispatch(action: BarchartAction): Unit = {
    val updatedAppState = appState.now.copy(
      barchartConfig = barchartReducer(
        appState.now.barchartConfig,
        action
      )
    )
    appState() = updatedAppState
  }

  def sliderChange(
      rowIndex: Int,
      newFilter: NumericalFilterParameter
  ): Unit = {
    dispatch(UpdateFilter(rowIndex, newFilter))
  }
}
