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
import info.vizierdb.serialized.CommandArgument
import info.vizierdb.ui.components.dataset.Dataset
import info.vizierdb.serialized.EnumerableValueDescription
import info.vizierdb.artifacts.VegaRegressionMethod


class ChartEditor(
    override val delegate: ModuleEditorDelegate,
    override val packageId: String,
    override val command: serialized.PackageCommand,
    val chartType: String
)(implicit owner: Ctx.Owner)
    extends DefaultModuleEditor(packageId, command, delegate)
    with Logging {
    // Keeps State of the arguments after editing
    println(chartType)
    override def loadState(arguments: Seq[CommandArgument]): Unit = {
    // This function should translate arguments into a new datsetRows()
    for (arg <- arguments){
        arg.id match {
        case "series" => 
            arg.value match {
            case JsArray(series) => 
                datasetRows() = series.map { row =>
                new ChartRow()
                }
            case _ => 
                println("Error: Expected JsArray")
            
            }
        case "artifact" =>
            arg.value match {
            case JsString(artifact) =>
                Rx{datasetRows().head.artifact.value(artifact)}
            case _ =>
                println("Error: Expected JsString")
            }
        }
    }
    }

    override def currentState: Seq[CommandArgument] = 
    {
    chartType match{
        case "scatterplot" => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                Json.toJson(Seq(
                row.dataset.toArgument,
                row.xColumn.toArgument,
                // CommandArgument("yList", JsArray(row.yColumns.now.map { yCol =>
                //     Json.toJson(yCol.yColumn.toArgument)}
                // )),
                row.yColumns.now.head.yColumn.toArgument,
                row.filter.toArgument,
                row.label.toArgument,
                row.color.toArgument,
                row.regression.toArgument
                ))})),
                datasetRows.now.head.artifact.toArgument)
        case "cdf" => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                Json.toJson(Seq(
                row.dataset.toArgument,
                row.xColumn.toArgument,
                row.filter.toArgument
                ))})),
                datasetRows.now.head.artifact.toArgument)
        case _ => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                Json.toJson(Seq(
                row.dataset.toArgument,
                row.xColumn.toArgument,
                CommandArgument("yList", JsArray(row.yColumns.now.map { yCol =>
                    Json.toJson(Seq(yCol.yColumn.toArgument))}
                )),
                row.filter.toArgument,
                row.label.toArgument,
                row.color.toArgument
                ))})),
                datasetRows.now.head.artifact.toArgument)
        }
    }

    val datasetRows = Var[Seq[ChartRow]](Seq())

    def appendChartRow(): Unit =
        datasetRows() = datasetRows.now :+ new ChartRow()
        // datasetRows() = datasetRows.now :+ BarChart

    class ChartRow(){
        val datasetProfile: Var[Option[PropertyList.T]] = Var(None)
        val xColumnData = Var[Option[Int]](None)

        val dataset: ArtifactParameter =
            new ArtifactParameter(
            id = "dataset",
            name = "Dataset",
            artifactType = ArtifactType.DATASET,
            artifacts = delegate.visibleArtifacts
                .map { _.mapValues { _._1.t } },
            required = true,
            hidden = false
            )

        val xColumn: ColIdParameter = 
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

        val yColumns = Var[Seq[ChartYColumn]](Seq())

        def appendYColumn(): Unit =
            yColumns() = yColumns.now :+ new ChartYColumn(dataset)

        appendYColumn()

        val label =
            new StringParameter(
                "label",
                "Label",
                true,
                false,
                ""
            )
        
        val filter =
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
        val sort =
            new EnumerableParameter(
                "sort",
                "Sort",
                Seq(
                    EnumerableValueDescription.apply(true,"---",""),
                    EnumerableValueDescription.apply(false,"Ascending","ascending"),
                    EnumerableValueDescription.apply(false,"Decending","decending"),

                ),
                true,
                false
            )
        
        val color =
            new ColorParameter(
            "color",
            "Color",
            true,
            false,
            Some("#214478")
            )

        val artifact =
            new StringParameter(
            "artifact",
            "Output Artifact (blank to show only)",
            false,
            false,
            ""
            )
        
        val regression = 
            new EnumerableParameter(
            "regression",
            "Regression",
            Seq(
                EnumerableValueDescription.apply(true,"---",""),
                EnumerableValueDescription.apply(false,"Linear",VegaRegressionMethod.Linear.key),
                EnumerableValueDescription.apply(false,"Logarithmic",VegaRegressionMethod.Logarithmic.key),
                EnumerableValueDescription.apply(false,"Exponential",VegaRegressionMethod.Exponential.key),
                EnumerableValueDescription.apply(false,"Power",VegaRegressionMethod.Power.key),
                EnumerableValueDescription.apply(false,"Quadratic",VegaRegressionMethod.Quadratic.key)
            ),
            true,
            false,
            )
        
        val chartTitle = 
            new StringParameter(
            "chartTitle",
            "Chart Title",
            true,
            false,
            ""
            )
        
        val xAxisTitle =
            new StringParameter(
            "xAxisTitle",
            "X-Axis Title",
            true,
            false,
            ""
            )
        
        val yAxisTitle =
            new StringParameter(
            "yAxisTitle",
            "Y-Axis Title",
            true,
            false,
            ""
            )

        val chartLegend = 
            new EnumerableParameter(
                "legend",
                "Legend",
                Seq(
                    EnumerableValueDescription.apply(true,"---",""),
                    EnumerableValueDescription.apply(false,"Top","top"),
                    EnumerableValueDescription.apply(false,"Bottom","bottom"),
                    EnumerableValueDescription.apply(false,"Left","left"),
                    EnumerableValueDescription.apply(false,"Right","right")
                ),
                true,
                false
            )
        


        val root = 
            chartType match{
                case "scatterplot" => 
                    fieldset(
                    legend("Scatter"),
                    table(
                        `class` := "parameter_list",
                        thead(
                        tr(
                            th("Dataset"),
                            th("X"),
                            th("Y"),
                            th("Filter"),
                            th("Sort"),
                            th("Regression")
                        )
                        ),
                        tbody( 
                        tr(
                            td(dataset.root),
                            td(xColumn.root),
                            td(
                            yColumns.map { _.map { _.root } }.reactive
                            ),
                            td(filter.root),
                            td(sort.root),
                            td(regression.root)
                        ) 
                        )
                    )
                    ).render
                case "barchart" =>
                        fieldset(
                        legend("Bars"),
                        table(
                            `class` := "parameter_list",
                            thead(
                            tr(
                                th("Dataset"),
                                th("X"),
                                th("Y"),
                                th("Filter"),
                                th("Sort"),
                            )
                            ),
                            tbody( 
                            tr(
                                td(dataset.root),
                                td(xColumn.root),
                                td(
                                yColumns.map { _.map { _.root } }.reactive
                                ),
                                td(filter.root),
                                td(sort.root)
                            ) 
                            )
                        )
                        ).render
                case "line-chart" =>
                        fieldset(
                        legend("Lines"),
                        table(
                            `class` := "parameter_list",
                            thead(
                            tr(
                                th("Dataset"),
                                th("X"),
                                th("Y"),
                                th("Filter"),
                                th("Sort"),
                            )
                            ),
                            tbody( 
                            tr(
                                td(dataset.root),
                                td(xColumn.root),
                                td(
                                yColumns.map { _.map { _.root } }.reactive
                                ),
                                td(filter.root),
                                td(sort.root)
                            ) 
                            )
                        )
                        ).render
                case "cdf" =>
                    fieldset(
                    legend("CDF"),
                    table(
                        `class` := "parameter_list",
                        thead(
                        tr(
                            th("Dataset"),
                            th("X"),
                            th("Filter"),
                            th("Sort"),
                        )
                        ),
                        tbody( 
                        tr(
                            td(dataset.root),
                            td(xColumn.root),
                            td(filter.root),
                            td(sort.root)
                        ) 
                        )
                    )
                    ).render
                    }
        }
        class ChartYColumn(dataset: ArtifactParameter)
        {
        val yColumn =
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
        val root =
            div(yColumn.root)
        }
    

    // val BarChart = new ChartRow()
    appendChartRow() 

    object SideMenu {
        private var isOpen = false
        private var sideMenuOpen = false
        private var chartConfigOpen = false
        
        val sideMenuContent =  div(`class` := "sidemenu", style := "visibility: visible").render

        def getIsOpen(): Boolean = {
            isOpen
        }

        def getSideMenuOpen(): Boolean = {
            sideMenuOpen
        }

        def getChartConfigOpen(): Boolean = {
            chartConfigOpen
        }

        def toggleMenu(buttonEvent: dom.MouseEvent, chartType: Boolean): Unit = {
            if (isOpen) {
                hide()
                isOpen = false
            } else {
                if (chartType) {
                    if (chartConfigOpen){
                        hide()
                    }
                    showAt(buttonEvent.pageX + 50, buttonEvent.pageY + 50)
                    isOpen = true
                    SideMenu.renderSideMenuContent()
                } else {
                    if (sideMenuOpen){
                        hide()
                    }
                    showAt(buttonEvent.pageX + 50, buttonEvent.pageY + 50)
                    isOpen = true
                    SideMenu.renderChartContent()
                }
            }
            } 

        def renderChartContent(): Unit = {
        while (sideMenuContent.firstChild != null) {
            sideMenuContent.removeChild(sideMenuContent.firstChild)
        }
        sideMenuContent.appendChild(div(
            table(
            `class` := "sidemenu_table",
            thead(
                tr(
                td("Chart Title"),
                td("X-Axis Title"),
                td("Y-Axis Title"),
                td("Legend")
                )
            ),
            tbody(
                tr(
                    td(Rx{
                        datasetRows().map(
                            row => {
                                row.chartTitle.root})}.reactive),
                    td(Rx{
                        datasetRows().map(
                            row => {
                                row.xAxisTitle.root})}.reactive),
                    td(Rx{
                        datasetRows().map(
                            row => {
                                row.yAxisTitle.root})}.reactive),
                    td(Rx{
                        datasetRows().map(
                            row => {
                                row.chartLegend.root})}.reactive)
                )
            ))
        ).render)
        }

        def renderSideMenuContent(): Unit = {
            while (sideMenuContent.firstChild != null) {
            sideMenuContent.removeChild(sideMenuContent.firstChild)}
            
            sideMenuContent.appendChild(div(
            table(
                `class` := "sidemenu_table",
                thead(
                tr(
                    th("Label"),
                    th("Color"),
                )
                ),
                tbody(
                tr(
                    td(Rx{
                        datasetRows().map(
                            row => {
                                row.label.root})}.reactive
                        ),
                    td(Rx{
                        datasetRows().map(
                            row => {
                                row.color.root})}.reactive
                        )
                )
            ) 
            )).render)
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

    //Reactive Val for sideMenuElement


    //css class for type of chart editor

    // What is displayed to users
    override val editorFields =
        div(`class` := chartType+"_chart_editor", 
            Rx {
                div(datasetRows().map(_.root))
            }.reactive,
            div(`class` := "sidemenu_container",
                button(`class` := "sidemenu_button", 
                "Line Config",
                onclick := { (e: dom.MouseEvent) => 
                    SideMenu.toggleMenu(e,true) }, 
                FontAwesome("ellipsis-v")), 
            SideMenu.sideMenuContent),
            div(`class` := "chartConfig_container",
                button(`class` := "chartConfig_button", 
            "Chart Config",
                onclick := { (e: dom.MouseEvent) => 
                    SideMenu.toggleMenu(e,false) }, 
            FontAwesome("cog")), 
            SideMenu.sideMenuContent)
    )

}


//Side Menu for Bar Chart Editor for Label, Filter, and Color


// BarChart Single Row Instance for Controlled Variables outside of Parameter Class
