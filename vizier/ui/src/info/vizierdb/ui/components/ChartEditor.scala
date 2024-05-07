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
import info.vizierdb.serializers
import info.vizierdb.ui.components._
import info.vizierdb.util.Logger
import scala.collection.mutable


class ChartEditor(
    override val delegate: ModuleEditorDelegate,
    override val packageId: String,
    override val command: serialized.PackageCommand,
    val chartType: String
)(implicit owner: Ctx.Owner) extends DefaultModuleEditor(packageId, command, delegate) with Logging {
    // Keeps State of the arguments after editing
    override def loadState(arguments: Seq[CommandArgument]): Unit = {
    // This function should translate arguments into a new datsetRows()
    for (arg <- arguments){
        arg.id match {
        case "series" => 
            arg.value match {
            case JsArray(series) => Rx{
                datasetRows().map { row =>
                    series.foreach { series =>
                        series match {
                            case JsArray(series) => 
                                row.dataset.set(series(0))
                                row.xColumn.set(series(1))
                                
                            case _ => 
                                logger.error("Error: Expected JsArray")
                        }
                    }
                }
            }
            case _ => 
                logger.error("Error: Expected JsArray")
            
            }
        case "artifact" =>
            arg.value match {
            case JsString(artifact) =>
                Rx{datasetRows().head.artifact.value(artifact)}
            case _ =>
                logger.error("Error: Expected JsString")
            }
        case "xTitle" =>
            arg.value match {
                case JsString(xTitle) =>
                    Rx{datasetRows().head.xAxisTitle.value(xTitle)}
                case _ =>
                    logger.error("Error: Expected JsString")
            }
        case "yTitle" =>
            arg.value match {
                case JsString(yTitle) =>
                    Rx{datasetRows().head.yAxisTitle.value(yTitle)}
                case _ =>
                    logger.error("Error: Expected JsString")
            }
        case "chartTitle" =>
            arg.value match {
                case JsString(chartTitle) =>
                    Rx{datasetRows().head.chartTitle.value(chartTitle)}
                case _ =>
                    logger.error("Error: Expected JsString")
            }
        case "legend" =>
            arg.value match {
                case JsString(legend) =>
                    Rx{datasetRows().head.chartLegend.value(legend)}
                case _ =>
                    logger.error("Error: Expected JsString")
            }
        case "sort" =>
            arg.value match {
                case JsString(sort) =>
                    Rx{datasetRows().head.sort.value(sort)}
                case _ =>
                    logger.error("Error: Expected JsString")
            }
        case _ =>
            logger.error("Error: Unknown argument")
        }}
    }


    override def currentState: Seq[CommandArgument] = 
    {
    chartType match{
        case "scatterplot" => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                Json.toJson(Seq(
                row.dataset.toArgument,
                row.xColumn.toArgument,
                row.yColumns.now.head.yColumn.toArgument,
                row.filter.toArgument,
                row.label.toArgument,
                CommandArgument("color", row.color.value),
                row.regression.toArgument
                ))})),
                datasetRows.now.head.artifact.toArgument,
                datasetRows.now.head.xAxisTitle.toArgument,
                datasetRows.now.head.yAxisTitle.toArgument,
                datasetRows.now.head.chartTitle.toArgument,
                datasetRows.now.head.chartLegend.toArgument
                )
        case "cdf" => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                Json.toJson(Seq(
                row.dataset.toArgument,
                row.xColumn.toArgument,
                row.filter.toArgument
                ))})),
                datasetRows.now.head.artifact.toArgument,
                datasetRows.now.head.xAxisTitle.toArgument,
                datasetRows.now.head.yAxisTitle.toArgument,
                datasetRows.now.head.chartTitle.toArgument,
                datasetRows.now.head.chartLegend.toArgument
                )
        case _ => 
            Seq(CommandArgument("series",JsArray(datasetRows.now.map { row =>
                Json.toJson(Seq(
                row.dataset.toArgument,
                row.xColumn.toArgument,
                CommandArgument("yList", JsArray(row.yColumns.now.map { yCol =>
                    Json.toJson(Seq(yCol.yColumn.toArgument))}
                )),
                CommandArgument("filter", JsString("")),
                row.label.toArgument,
                CommandArgument("color", row.color.value),
                ))})),
                datasetRows.now.head.artifact.toArgument,
                datasetRows.now.head.xAxisTitle.toArgument,
                datasetRows.now.head.yAxisTitle.toArgument,
                datasetRows.now.head.chartTitle.toArgument,
                datasetRows.now.head.chartLegend.toArgument,
                datasetRows.now.head.sort.toArgument
                )
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
            new StringParameter(
                "filter",
                "Filter",
                false,
                false,
                ""
            )

        val filterSeq = Var[Seq[Filter]](Seq())

        val sort =
            new EnumerableParameter(
                "sort",
                "Sort",
                Seq(
                    EnumerableValueDescription.apply(true,"---",""),
                    EnumerableValueDescription.apply(false,"Ascending","ascending"),
                    EnumerableValueDescription.apply(false,"Descending","descending"),
                ),
                true,
                false
            )
        
        val color = new Color()

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
            "xTitle",
            "X-Axis Title",
            true,
            false,
            ""
            )
        
        val yAxisTitle =
            new StringParameter(
            "yTitle",
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
                    EnumerableValueDescription.apply(false,"Top Right","top-right"),
                    EnumerableValueDescription.apply(false,"Bottom Right","bottom-right"),
                    EnumerableValueDescription.apply(false,"Top Left","top-left"),
                    EnumerableValueDescription.apply(false,"Botton Left","bottom-left")
                ),
                true,
                false
            )
        
        val testFilter = new Filter(dataset,filter, datasetProfile)


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
                                    appendChartRow()}
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
                                td(testFilter.root.reactive),
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
                                    },
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
        
    val datasetRowLabel = Rx{
        val label = datasetRows().flatMap(
            row => {
                row.yColumns().map(
                    yCol => {
                        yCol.label.root
                    }
                )
            }
        )
    val categoryLabel = datasetRows()
            .filter(_.category.category.selectedColumn() != None)
            .map { row => 
                row.category.label.root
        }
        label ++ categoryLabel
    }

    val datasetRowColor = Rx{
        val colors = datasetRows().flatMap { row => 
            row.yColumns().map { yCol => 
            yCol.color.root
            }
        }
        val categoryColors = datasetRows()
            .filter(_.category.category.selectedColumn() != None)
            .map { row => 
                row.category.color.root
        }
        colors ++ categoryColors
    }

    val renderSideMenuContent =
        Rx {
            val datalabels = datasetRowLabel()
            val datacolors = datasetRowColor()

            table(
                `class` := "sidemenu_table",
                thead(
                    tr(th("Label")),
                    tr(th("Color"))
                ),
                (datalabels zip datacolors).map { case (label, color) =>
                    tbody(
                        tr(td(label)),
                        tr(td(color))
                    )
                }
            )
        }.reactive

//    val renderSideMenuContent =
//        Rx {
//                table(
//                    `class` := "sidemenu_table",
//                    thead(
//                        tr(th("Label")),
//                        tr(th("Color"))
//                        ),
//                    tbody(
//                        datasetRowLabel().map(
//                            label => {
//                                tr(td(label))
//                            }
//                        ),
//                        datasetRowColor().map(
//                            color => {
//                                tr(td(color))
//                            }
//                        )
//                    )
//                )
//            }.reactive
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

    class Filter (
        val dataset:ArtifactParameter, 
        val filter:StringParameter, 
        val datasetProfile:Var[Option[PropertyList.T]]) extends Logging {

        val profiler_dump = Var[Option[PropertyList.T]](None)
        val seqProfiledInfo = Var[Seq[ColumnStats]](Seq())
        val maxFilterValue = Var[Option[Double]](None)
        val columnName = Var[Option[String]](None)
        val selectedFilterValue = Var[Option[Int]](None)

        sealed trait ColumnStats {
            def columnName: String
            def columnType: String
        }

        case class Numerical(
            columnName: String,
            columnType: String,
            minValue: Double,
            maxValue: Double,
            mean: Double,
            slider: Frag
        ) extends ColumnStats

        case class Categorical
        (
            columnName: String,
            columnType: String,
            distinctValuesCount: Int,
            nullCount: Int,
        ) extends ColumnStats

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
                                    profiler_dump() = Some(ds.properties)
                                case _ => None
                            }
                        case Failure(e) =>
                            None
                    }
                }
            }

        Rx {
            profiler_dump() match {
                case None => 
                    None
                case Some(profile) =>
                    val profiledData = profile(2).value.as[JsArray].value// Assuming 'profile' itself is the array
                    profiledData.foreach { column =>
                        println(column.toString())
                        val columnData = column.as[JsObject]
                        val columnName = (columnData \ "column" \ "name").as[String]
                        val columnType = (columnData \ "column" \ "type").as[String]
                        if (columnType == "integer" || columnType == "double") { // Assuming numerical data could be integer or double
                            val minValue = (columnData \ "min").as[Double]
                            val maxValue = (columnData \ "max").as[Double]
                            val mean = (columnData \ "mean").as[Double]
                            seqProfiledInfo() = seqProfiledInfo.now :+ Numerical(columnName, columnType, minValue, maxValue, mean, sliderInput(maxValue.toInt))
                        } else if (columnType == "string") {
                            val distinctValuesCount = (columnData \ "distinctValueCount").as[Int]
                            val nullCount = (columnData \ "nullCount").as[Int]
                            seqProfiledInfo() = seqProfiledInfo.now :+ Categorical(columnName, columnType, distinctValuesCount, nullCount)
                        }
                    }
            }
        }

        val availableColumns = Rx {
                    dataset.selectedDataset() match {
                    case None => Seq.empty
                    case Some(datasetId) =>
                        delegate.visibleArtifacts().get(datasetId) match {
                        case Some((ds: DatasetSummary, _))     => ds.columns
                        case Some((ds: DatasetDescription, _)) => ds.columns
                        case None                              => Seq.empty
                        }
                    }
                }


        

        /** A unique identifier for the parameter; the key in the module arguments
            */
        val id = "filter"

        /** A human-readable name for the parameter.
            */
        val name = "Filter"

        /** Callbacks to trigger when the value of the element changes
            */
        private val changeHandlers = mutable.Buffer[dom.Event => Unit]()

        /** Register code to run when the element's value changes
            */
        def onChange(handler: dom.Event => Unit) =
            changeHandlers.append(handler)
            
        def field(
            basetag: String,
            attrs: AttrPair*
        )(elems: Frag*): Frag = {
            val identity = s"parameter_${Parameter.nextInputId}"
            div(
            `class` := "parameter",
            label(attr("for") := identity, name),
            tag(basetag)(
                attrs,
                `class` := Parameter.PARAMETER_WIDGET_CLASS,
                attr("id") := identity,
                attr("name") := name,
                elems
            ),
            onchange := { (e: dom.Event) => changeHandlers.foreach { _(e) } }
            )
        }

        def pulldown(
            selected: Int
        )(options: (String, String)*): Frag =
            field("select")(
            options.zipWithIndex.map { case ((description, value), idx) =>
                option(
                attr("value") := value,
                if (idx == selected) { attr("selected", raw = true) := "" }
                else { "" },
                description
                )
            }: _*
            )


                
        val pulldownColumns = Rx {
            val options = ("---", "none") +: availableColumns().map { v => (v.name, v.id.toString) }
            pulldown(
                availableColumns().zipWithIndex.find(_._1 == columnName.now.getOrElse("")).map(_._2 + 1).getOrElse(0)
            )(options: _*).render.asInstanceOf[dom.html.Select]
        }

        onChange { e =>
            val selectElement = e.target.asInstanceOf[dom.html.Select]
            val selectedValue = selectElement.options(selectElement.selectedIndex).value
            selectedFilterValue() = Some(selectedValue.toInt)
        }



        def sliderInput(maxVal:Int) : Frag = {
            input(
            scalatags.JsDom.all.id := "filter_slider",
            `type` := "range",
            min := 0,
            max := maxVal)}

        val stringInput = input(
            `type` := "text",
            placeholder := "Enter Filter"
        )


        val root = Rx {
            selectedFilterValue() match {
                case None =>
                    div(
                        pulldownColumns.reactive,
                    )
                case Some(value) =>
                    seqProfiledInfo.now(value) match {
                        case Numerical(columnName, columnType, minValue, maxValue, mean, slider) =>
                            div(
                                pulldownColumns.reactive,
                                slider.render,
                            )
                        case Categorical(columnName, columnType, distinctValuesCount, nullCount) =>
                            div(
                                pulldownColumns.reactive,
                                stringInput
                            )
                    }
            }
        }
}

    class Color{

        val selectedColor = Var("1")

        val root =
            div(
            `class` := "color_parameter",
            (1 to 6).map { i =>
                label (
                span(
                input(
                    `type` := "radio",
                    scalatags.JsDom.all.name := "radioButton",
                    scalatags.JsDom.all.value := i,
                    onchange := { (e: dom.Event) =>
                    selectedColor() = i.toString()
                    }
                )
                )
                )
            }
            ).render

        def value = {
            selectedColor.now match {
            case "1" => JsString("#000000")
            case "2" => JsString("#FFFFFF")
            case "3" => JsString("#214478")
            case "4" => JsString("#FF0000")
            case "5" => JsString("#FFD600")
            case "6" => JsString("#00B507")
            }}

    }
}


