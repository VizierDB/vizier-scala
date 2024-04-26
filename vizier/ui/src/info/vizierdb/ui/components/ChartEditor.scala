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
            }}}}

    override def currentState: Seq[CommandArgument] = 
    {
    println(datasetRows.now)
    chartType match{
        case "scatterplot" => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                row.category.category.selectedColumn.now match {
                    case Some(categoryValueId) =>
                        Json.toJson(Seq(
                        row.dataset.toArgument,
                        row.xColumn.toArgument,
                        row.yColumns.now.head.yColumn.toArgument,
                        row.category.category.toArgument,
                        row.filter.toArgument,
                        row.label.toArgument,
                        row.color.toArgument,
                        row.regression.toArgument
                        ))
                    case None =>
                        Json.toJson(Seq(
                        row.dataset.toArgument,
                        row.xColumn.toArgument,
                        row.yColumns.now.head.yColumn.toArgument,
                        row.filter.toArgument,
                        row.label.toArgument,
                        row.color.toArgument,
                        row.regression.toArgument
                        ))
                
                }
                })),
                datasetRows.now.head.artifact.toArgument)
        case "cdf" => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                Json.toJson(Seq(
                row.dataset.toArgument,
                row.xColumn.toArgument,
                row.filter.toArgument
                ))})),
                datasetRows.now.head.artifact.toArgument)
        case "line-chart" => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                row.category.category.selectedColumn.now match {
                    case Some(categoryValueId) =>
                        Json.toJson(Seq(
                        row.dataset.toArgument,
                        row.xColumn.toArgument,
                        CommandArgument("yList", JsArray(row.yColumns.now.map { yCol =>
                            Json.toJson(Seq(yCol.yColumn.toArgument))}
                        )),
                        row.category.category.toArgument,
                        row.filter.toArgument,
                        row.label.toArgument,
                        row.color.toArgument
                        ))
                    case None =>
                        Json.toJson(Seq(
                        row.dataset.toArgument,
                        row.xColumn.toArgument,
                        CommandArgument("yList", JsArray(row.yColumns.now.map { yCol =>
                            Json.toJson(Seq(yCol.yColumn.toArgument))}
                        )),
                        row.filter.toArgument,
                        row.label.toArgument,
                        row.color.toArgument
                        ))
                }
            
            })),
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
        // datasetRows() = datasetRows.now :+ new ChartRow()
        // datasetRows() = datasetRows.now :+ BarChart
        datasetRows.update(_ :+ new ChartRow())
    class ChartRow(){
        val datasetProfile: Var[Option[PropertyList.T]] = Var(None)

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
        
        val category = new ChartCategoryColumn(dataset)

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
                Rx {
                    xColumn.selectedColumn() match {
                    case None      => 0
                    case Some(col) => {
                        col
                    }
                    }
                },
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
                false,
                false
            )
        
        dataset.selectedDataset.triggerLater{
            _ match {
                case None => 
                    datasetProfile() = None
                case Some(ds) =>
                    Vizier.api.artifactGet(
                        Vizier.project.now.get.projectId,
                        delegate.visibleArtifacts.now.get(dataset.selectedDataset.now.get).get._1.id,
                        limit = Some(0),
                        profile = Some("true")
                    ).onComplete{
                        case Success(artifactDescription) => 
                            artifactDescription match {
                                case ds: DatasetDescription => 
                                    datasetProfile() = Some(ds.properties)
                                    println("Working Profiled Data")
                                case _ => None
                            }
                        case Failure(e) =>
                            println(e)
                            None
                    }
                }
            }

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
                    div(
                    table(
                        `class` := "parameter_list",
                        thead(
                        tr(
                            th("Dataset"),
                            th("X"),
                            th("Y"),
                            th("Category"),
                            th("Filter"),
                            th("Regression"),
                            th(""),
                        )
                        ),
                        tbody( 
                        tr(
                            td(dataset.root),
                            td(xColumn.root),
                            td(
                            yColumns.map { cols =>
                                div(
                                    cols.map(_.root),
                                    button("+", onclick := { () => 
                                        appendYColumn()
                                    }),
                                    button("-", onclick := { () => 
                                        yColumns() = yColumns.now.dropRight(1)
                                    })
                                )
                            }.reactive
                            ),
                            td(category.category.root),
                            td(filter.root),
                            td(regression.root),
                            td(button(`class` := "add_row_button",
                                FontAwesome("plus"),
                                onclick := { (e: dom.MouseEvent) =>
                                    appendChartRow()
                                },
                                ),
                                button(`class` := "remove_row_button",
                                    FontAwesome("minus"),
                                onclick := { (e: dom.MouseEvent) =>
                                    datasetRows() = datasetRows.now.dropRight(1)
                                },
                                )))))
                    ).render
                case "barchart" =>
                        div(
                        table(
                            `class` := "parameter_list",
                            thead(
                            tr(
                                th("Dataset"),
                                th("X"),
                                th("Y"),
                                th("Filter"),
                                th("Sort"),
                                th(""),
                            )
                            ),
                            tbody( 
                            tr(
                                td(dataset.root),
                                td(xColumn.root),
                                td(
                                yColumns.map { cols =>
                                    div(
                                        cols.map(_.root),
                                        button(`class`:= "add_y_column_button",
                                            "+", onclick := { () => 
                                            appendYColumn()
                                        }),
                                        button(`class`:= "remove_y_column_button",
                                            "-", onclick := { () => 
                                            yColumns() = yColumns.now.dropRight(1)
                                        })
                                    )
                                }.reactive
                                ),
                                td(filter.root),
                                td(sort.root),
                                td(button(`class` := "add_row_button",
                                    FontAwesome("plus"),
                                    onclick := { (e: dom.MouseEvent) =>
                                        appendChartRow()
                                    },
                                    ),
                                button(`class` := "remove_row_button",
                                    FontAwesome("minus"),
                                    onclick := { (e: dom.MouseEvent) =>
                                        datasetRows() = datasetRows.now.dropRight(1)
                                    }),
                                    ))))
                        ).render
                case "line-chart" =>
                        div(
                        table(
                            `class` := "parameter_list",
                            thead(
                            tr(
                                th("Dataset"),
                                th("X"),
                                th("Y"),
                                th("Category"),
                                th("Filter"),
                                th("Sort"),
                                th(""),
                            )
                            ),
                            tbody( 
                            tr(
                                td(dataset.root),
                                td(xColumn.root),
                                td(
                                div(
                                yColumns.map { cols =>
                                    div(
                                        cols.map(_.root),
                                        button(`class`:= "add_y_column_button",
                                            "+", onclick := { () => 
                                            appendYColumn()
                                        }),
                                        button(`class`:= "remove_y_column_button",
                                            "-", onclick := { () => 
                                            yColumns() = yColumns.now.dropRight(1)
                                        })
                                    )
                                }.reactive
                            )
                                ),
                                td(category.category.root),
                                td(filter.root),
                                td(sort.root),
                                td(button(`class` := "add_row_button",
                                FontAwesome("plus"),
                                    onclick := { (e: dom.MouseEvent) =>
                                        appendChartRow()
                                    },
                                    ),
                                td(button(`class` := "remove_row_button",
                                    FontAwesome("minus"),
                                    onclick := { (e: dom.MouseEvent) =>
                                        datasetRows() = datasetRows.now.dropRight(1)
                                    }),
                                    ))
                            ) 
                            )
                        )
                        ).render
                case "cdf" =>
                    div(
                    table(
                        `class` := "parameter_list",
                        thead(
                        tr(
                            th("Dataset"),
                            th("X"),
                            th("Filter"),
                            th(""),
                        )
                        ),
                        tbody( 
                        tr(
                            td(dataset.root),
                            td(xColumn.root),
                            td(filter.root),
                            td(button(`class` := "add_row_button",
                                FontAwesome("plus"),
                                onclick := { (e: dom.MouseEvent) =>
                                    appendChartRow()
                                }),
                                button(`class` := "remove_row_button",
                                    FontAwesome("minus"),
                                onclick := { (e: dom.MouseEvent) =>
                                    datasetRows() = datasetRows.now.dropRight(1)
                                }),
                                ))) 
                    ),
                    ).render
                    }
        }

        class ChartCategoryColumn(dataset: ArtifactParameter)
        {
        val category =
            new ColIdParameter(
            "category",
            "Category",
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
        val label =
            new StringParameter(
            "label",
            "Label",
            true,
            false,
            ""
            )
        val color =
            new ColorParameter(
            "color",
            "Color",
            true,
            false,
            Some("#214478")
            )
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
        val label =
            new StringParameter(
            "label",
            "Label",
            true,
            false,
            ""
            )
        val color =
            new ColorParameter(
            "color",
            "Color",
            true,
            false,
            Some("#214478")
        )
        val root =
            div(yColumn.root)
        }
    

    // val BarChart = new ChartRow()
    appendChartRow() 

    object SideMenu extends Enumeration{
        val Closed, ChartConfiguration, LineConfiguration = Value
        
        val currentConfigState = Var(Closed)

        val sideMenuContent = Rx {
            currentConfigState() match {
                case Closed => 
                    div()
                case ChartConfiguration => 
                    div(
                        renderChartContent.render
                    )
                case LineConfiguration => 
                    div(
                        renderSideMenuContent.render
                    )
            }
        }.reactive

    val renderChartContent =
        Rx{
            table(
            `class` := "sidemenu_table",
            thead(
                tr(
                th("Chart Title"),
                th("X-Axis Title"),
                th("Y-Axis Title"),
                th("Legend")
                )
            ),
            tbody(
                tr(
                    td(
                        datasetRows().map(
                            row => {
                                row.chartTitle.root})),
                    td(
                        datasetRows().map(
                            row => {
                                row.xAxisTitle.root})),
                    td(
                        datasetRows().map(
                            row => {
                                row.yAxisTitle.root})),
                    td(
                        datasetRows().map(
                            row => {
                                row.chartLegend.root}))
                )
            ))
        }.reactive
        
    def datasetRowLabel = Rx{
        datasetRows().flatMap(
            row => {
                row.yColumns().map(
                    yCol => {
                        yCol.label.root
                    }
                )
            }
        ) ++ datasetRows().map(
            row => {
                row.category.label.root
            }
        )
    }

    def datasetRowColor = Rx{
        datasetRows().flatMap(
            row => {
                row.yColumns().map(
                    yCol => {
                        yCol.color.root
                    }
                )
            }
        ) ++ 
        datasetRows().map(
            row => {
                row.category.color.root
            }
        )
    }

    val renderSideMenuContent =     
        Rx {
                table(
                    `class` := "sidemenu_table",
                    thead(
                        tr(th("Label")),
                        tr(th("Color"))
                        ),
                    tbody(
                        tr(td(datasetRowLabel())),
                        tr(td(datasetRowColor()))
                    )
                )
            }.reactive
    }
    override val editorFields =
        div(`class` := chartType+"_chart_editor", 
            Rx {
                fieldset(
                    legend(
                        chartType match {
                            case "scatterplot" => 
                                "Scatter"
                            case "barchart" => 
                                "Bars"
                            case "line-chart" => 
                                "Lines"
                            case "cdf" => 
                                "CDF"
                            case _ => 
                                "Chart"
                        }
                    ),
                div(datasetRows().map(_.root)))}.reactive,
            div(`class` := "sidemenu_container",
                button(`class` := "sidemenu_button",
                FontAwesome("ellipsis-v"),  
                chartType match {
                    case "scatterplot" => 
                        "Point"
                    case "barchart" => 
                        "Bar"
                    case "line-chart" => 
                        "Line"
                    case "cdf" => 
                        "Line"
                    case _ => 
                        "Chart"
                },
                onclick := { (e: dom.MouseEvent) => 
                    SideMenu.currentConfigState() = if(SideMenu.currentConfigState.now == SideMenu.LineConfiguration) SideMenu.Closed else SideMenu.LineConfiguration
                        },
                ),
            button(`class` := "chartConfig_button", 
            FontAwesome("cog"), 
            "Chart",
                onclick := { (e: dom.MouseEvent) => 
                    SideMenu.currentConfigState() = if(SideMenu.currentConfigState.now == SideMenu.ChartConfiguration) SideMenu.Closed else SideMenu.ChartConfiguration
                }),
            SideMenu.sideMenuContent
            ))

}


//Side Menu for Bar Chart Editor for Label, Filter, and Color


// BarChart Single Row Instance for Controlled Variables outside of Parameter Class
