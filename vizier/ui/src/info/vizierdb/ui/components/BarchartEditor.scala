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
import java.awt.Font
import scala.concurrent.Future
import info.vizierdb.serialized.EnumerableValueDescription

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

    val SeqBarChartValues = Seq(
      BarChart.dataset,
      BarChart.xColumn,
      BarChart.yColumn,
      BarChart.filter,
      BarChart.label,
      BarChart.color
    )

    val multipleYCol = JsArray(Seq(JsArray(Seq(
      Json.toJson(CommandArgument("ycol", BarChart.yColumn.head.value))
    ))))

    val commandDataset = CommandArgument("dataset", BarChart.dataset.value)
    val commandXColumn = CommandArgument("xcol", BarChart.xColumn.value)
    val commandYColumn = CommandArgument("yList", multipleYCol)
    val commandFilter = CommandArgument("filter", BarChart.filter.value)
    val commandLabel = CommandArgument("label", BarChart.label.value)
    val commandColor = CommandArgument("color", BarChart.color.value)

    val seqCommandValues = Seq(
      commandDataset,
      commandXColumn,
      commandYColumn,
      commandFilter,
      commandLabel,
      commandColor
    )

    val JsListArray = JsArray(Seq(JsArray(Seq(
      Json.toJson(commandDataset),
      Json.toJson(commandXColumn),
      Json.toJson(commandYColumn),
      Json.toJson(commandFilter),
      Json.toJson(commandLabel),
      Json.toJson(commandColor)
      ))))
      //List Parameter = JsArray(Seq(JsArray(Seq(Json.toJson(ListParamBarValue.toArgument)))

    val CommandArg = CommandArgument.apply("series",
      JsListArray)


    return Seq(
      CommandArg,
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
      Seq[String](""),
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

  def makeColor =
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

  def make_multi_select_parameter = 
    new MultiSelectParameter(
      "multi_select",
      "Multi Select",
      true,
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
      false
    )

  def make_aggregate_parameter =
    new AggregateParameter(
      "aggregate",
      "Aggregate",
      true,
      false,
      None
    )

  //Need this Rx[Seq[serialized.DatasetColumn]]
  val listParam_bar: ListParameter =
    new ListParameter(
      "series",
      "Bars",
      Seq[String]("Dataset", "X", "Y Params", "Filter", "Label", "Colors"),
      { () =>
        val currentDataset = makeDataset
        val xCol = makeXColumn(currentDataset)
        val newFilter = makeFilter(currentDataset, xCol)
        profiler(currentDataset, newFilter)
        xColChange(xCol, newFilter)
        Seq(
          currentDataset,
          xCol,
          makeyListParam(currentDataset),
          newFilter,
          makeLabel,
          makeColor
        )
      },
      true,
      false
    )

  def makeFilter(dataset:ArtifactParameter ,xColumn: ColIdParameter) =
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

  def profiler(dataset: ArtifactParameter,filter: NumericalFilterParameter){
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

  val dataset = makeDataset
  val xCol = makeXColumn(dataset)
  val yList = makeyListParam(dataset)
  val filter = makeFilter(dataset, xCol)

  val BarChart = new BarchartRow(
    dataset,
    xCol,
    Seq(makeYColumn(dataset)),
    filter,
    makeLabel,
    makeColor,
    make_aggregate_parameter,
    make_multi_select_parameter
  )

  val SeqBarChart = Seq(
    BarChart.dataset,
    BarChart.xColumn,
    BarChart.yColumn,
    BarChart.filter,
    BarChart.label,
    BarChart.color
  )
  
  BarChart.dataset.selectedDataset.trigger {
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
                    BarChart.filter.profile_data() = Some(ds.properties)

                    println("Profiled ")
                  case _ =>
                    Vizier.error("Not a dataset")
                }
              case Failure(exception) =>
                Vizier.error(exception.getMessage())
            }
      }
    }

  BarChart.xColumn.selectedColumn.trigger {
    _ match {
      case None      => println("No column selected")
      case Some(col) => BarChart.filter.updateXColumnData(Some(col))
    }
  }

  //"Filter", "Label", "Colors")

  val sideMenuElement =
    button(`class` := "sidemenu_button", onclick := { (e: dom.MouseEvent) => SideMenu.toggleMenu(e,BarChart) }, 
      FontAwesome("ellipsis-v")
    ).render

  val chartConfigElement =
    button(`class` := "chartConfig_button", onclick := { (e: dom.MouseEvent) => chartConfig.toggleMenu(e,BarChart) }, 
      FontAwesome("cog")
    ).render

  // What is displayed to users
  override val editorFields =
    div(`class` := "bar_chart_editor", BarChart.root,
      div(`class` := "sidemenu_container", sideMenuElement, SideMenu.sideMenuContent),
      div(`class` := "chartConfig_container", chartConfigElement, chartConfig.chartConfigContent)
    )
}

//Side Menu for Bar Chart Editor for Label, Filter, and Color
object SideMenu {

  private var isOpen = false
  
  val sideMenuContent =  div(`class` := "sidemenu", style := "visibility: visible").render

  def getIsOpen(): Boolean = {
    isOpen
  }

  def toggleMenu(buttonEvent: dom.MouseEvent, barChart:BarchartRow): Unit = {
    if (isOpen) {
      hide()
      isOpen = false
    } else {
      showAt(buttonEvent.pageX + 50, buttonEvent.pageY + 50)
      isOpen = true
      SideMenu.renderSideMenuContent(barChart)
    } 
  }

def renderSideMenuContent(barChart: BarchartRow): Unit = {
  while (sideMenuContent.firstChild != null) {
    sideMenuContent.removeChild(sideMenuContent.firstChild)
  }

  sideMenuContent.appendChild(div(
    table(
      `class` := "sidemenu_table",
      thead(
        tr(
          th("Label"),
          th("Filter"),
          th("Color")
        )
      ),
      tbody(
        tr(
          td(barChart.label.root),
          td(barChart.filter.root),
          td(barChart.color.root)
        )
      )
    ) 
  ).render)
}


  def showAt(x: Double, y: Double): Unit = {
    sideMenuContent.style.left = s"${x}px" // Target sideMenuContent 
    sideMenuContent.style.top = s"${y}px"
    sideMenuContent.style.visibility = "visible"
    sideMenuContent.style.opacity = "1.0"
  }

  def hide(): Unit = {
    sideMenuContent.style.visibility = "hidden" // Target sideMenuContent
    sideMenuContent.style.opacity = "0"
  }
}

object chartConfig {

  private var isOpen = false
  
  val chartConfigContent =  div(`class` := "chart_config", style := "visibility: visible").render

  def toggleMenu(buttonEvent: dom.MouseEvent, barChart:BarchartRow): Unit = {
    if (isOpen) {
      hide()
      isOpen = false
    } else {
      showAt(buttonEvent.pageX + 50, buttonEvent.pageY + 50)
      isOpen = true
      chartConfig.renderSideMenuContent(barChart)
    } 
  }

def renderSideMenuContent(barChart: BarchartRow): Unit = {
  while (chartConfigContent.firstChild != null) {
    chartConfigContent.removeChild(chartConfigContent.firstChild)
  }

  chartConfigContent.appendChild(div(
    table(
      `class` := "sidemenu_table",
      thead(
        tr(
          th("Aggregate"),
          th("Multi Select"),
        )
      ),
      tbody(
        tr(
          td(barChart.aggregate.root),
          td(barChart.multiSelect.root)
        )
      )
    ) 
  ).render)
}


  def showAt(x: Double, y: Double): Unit = {
  // Set left position
  chartConfigContent.style.left = s"${x}px"
  chartConfigContent.style.top = if (SideMenu.getIsOpen()) {
    (SideMenu.sideMenuContent.offsetHeight + y + 5).toString + "px"
  } else {
    (y + 5).toString + "px" // Normal position
  }

  // Set visibility and opacity
  chartConfigContent.style.visibility = "visible"
  chartConfigContent.style.opacity = "1.0"
  }

  def hide(): Unit = {
    chartConfigContent.style.visibility = "hidden" // Target sideMenuContent
    chartConfigContent.style.opacity = "0"
  }
}

// BarChart Single Row Instance for Controlled Variables outside of Parameter Class
case class BarchartRow(
    dataset: ArtifactParameter,
    xColumn: ColIdParameter,
    yColumn: Seq[ColIdParameter],
    filter: NumericalFilterParameter,
    label: StringParameter,
    color: ColorParameter,
    aggregate : AggregateParameter,
    multiSelect: MultiSelectParameter
){
  val titles = Seq("Dataset", "X", "Y") 

  val root =
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
        renderBarChartRow
      ),
    ).render

  def renderBarChartRow() = {
  tbody( 
    tr(
      td(dataset.root),
      td(xColumn.root),
      td(yColumn.head.root),
      ) 
    )
  }
}