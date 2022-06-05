/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.commands.plot

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.types.ArtifactType
import org.apache.spark.sql.{ DataFrame, Row }
import org.apache.spark.sql.functions.expr
import info.vizierdb.spark.caveats.QueryWithCaveats
import info.vizierdb.spark.caveats.QueryWithCaveats.ResultTooBig
import org.mimirdb.caveats.implicits._
import org.apache.spark.unsafe.types.UTF8String
import info.vizierdb.VizierException
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.vega.{ Vega, Chart }
import info.vizierdb.spark.SparkPrimitive
import info.vizierdb.vega.{
  AnyMark,
  MarkBar,
  MarkArea,
  MarkPoint,
  MarkLine,
  MarkDef
}
import info.vizierdb.vega.MarkDefPointAsBool
import info.vizierdb.vega.AutosizeTypeFitX
import info.vizierdb.catalog.CatalogDB

object SimpleChart extends Command
{
  val CHART_TYPE_AREA           = "Area Chart"
  val CHART_TYPE_BAR            = "Bar Chart"
  val CHART_TYPE_LINE_POINTS    = "Line Chart with Points"
  val CHART_TYPE_LINE_NO_POINTS = "Line Chart without Points"
  val CHART_TYPE_SCATTER        = "Scatter Plot"

  val PARAM_DATASET = "dataset"
  val PARAM_NAME = "name"
  val PARAM_SERIES = "series"
  val PARAM_SERIES_COLUMN = "series_column"
  val PARAM_SERIES_CONSTRAINT = "series_constraint"
  val PARAM_SERIES_LABEL = "series_label"
  val PARAM_XAXIS = "xaxis"
  val PARAM_XAXIS_COLUMN = "xaxis_column"
  val PARAM_XAXIS_CONSTRAINT = "xaxis_constraint"
  val PARAM_CHART = "chart"
  val PARAM_CHART_TYPE = "chartType"
  val PARAM_CHART_GROUPED = "chartGrouped"

  val MAX_RECORDS = 10000


  def name: String = "Column-Based Chart"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = PARAM_DATASET, name = "Dataset"),
    StringParameter(id = PARAM_NAME, name = "Chart Name", required = false),
    ListParameter(id = PARAM_SERIES, name = "Data Series", components = Seq(
      ColIdParameter(id = PARAM_SERIES_COLUMN, name = "Column"),
      // StringParameter(id = PARAM_SERIES_CONSTRAINT, name = "Constraint", required = false),
      StringParameter(id = PARAM_SERIES_LABEL, name = "Label", required = false),
    )),
    RecordParameter(id = PARAM_XAXIS, name = "X Axis", components = Seq(
      ColIdParameter(id = PARAM_XAXIS_COLUMN, name = "Column", required = false),
      StringParameter(id = PARAM_XAXIS_CONSTRAINT, name = "Constraint", required = false),
    )),
    RecordParameter(id = PARAM_CHART, name = "Chart", components = Seq(
      EnumerableParameter(id = PARAM_CHART_TYPE, name = "Type", values = EnumerableValue.withNames(
        "Area Chart"                -> CHART_TYPE_AREA,
        "Bar Chart"                 -> CHART_TYPE_BAR,
        "Line Chart with Points"    -> CHART_TYPE_LINE_POINTS,
        "Line Chart without Points" -> CHART_TYPE_LINE_NO_POINTS,
        "Scatter Plot"              -> CHART_TYPE_SCATTER
      ), default = Some(1), aliases = Map(
        "Line Chart" -> "Line Chart with Points"
      )),
      BooleanParameter(id = PARAM_CHART_GROUPED, name = "Grouped", required = false, default = Some(true)),
    ))
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.getRecord(PARAM_CHART).get[String](PARAM_CHART_TYPE)} ${arguments.pretty(PARAM_NAME)} FOR ${arguments.pretty(PARAM_DATASET)}"
  def title(arguments: Arguments): String = 
    s"${arguments.getRecord(PARAM_CHART).get[String](PARAM_CHART_TYPE)} of ${arguments.pretty(PARAM_DATASET)}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val datasetArtifact = context.artifact(datasetName)
                                 .getOrElse { throw new VizierException(s"Unknown dataset $datasetName") }
    var dataset = CatalogDB.withDB { implicit s => datasetArtifact.dataframe }()
    val schema = dataset.schema

    val filter = arguments.getRecord(PARAM_XAXIS)
                          .getOpt[String](PARAM_XAXIS_CONSTRAINT)
                          .getOrElse { "" }
    val xaxis = arguments.getRecord(PARAM_XAXIS)
                         .get[Int](PARAM_XAXIS_COLUMN)
    val yaxes: Seq[(Int, String)] 
              = arguments.getList(PARAM_SERIES)
                         .map { y => 
                            val col = y.get[Int](PARAM_SERIES_COLUMN)
                            (
                              col,
                              y.getOpt[String](PARAM_SERIES_LABEL) match { 
                                case None | Some("") => schema(col).name
                                case Some(x) => x.replaceAll("\\.", "")
                              }
                            )
                          }
    val name = arguments.getOpt[String](PARAM_NAME).getOrElse { "untitled chart" }
    
    if(filter != ""){
      dataset = dataset.filter(expr(filter))
    }

    val rows = dataset.take(MAX_RECORDS+1)

    if(rows.size > MAX_RECORDS){
      context.error(s"$datasetName has ${dataset.count} rows, but chart cells are limited to $MAX_RECORDS rows.  Either summarize the data first, or use a python cell to plot the data.")
      return
    }

    val data = 
      rows.map { row =>
        ((xaxis, schema(xaxis).name) +: yaxes).map { case (idx:Int, label:String) => 
          val column = schema(idx)
          label -> SparkPrimitive.encode(row(idx), column.dataType)
        }.toMap
      }.toSeq
    val chartOrMark:Either[Vega.BasicChart,AnyMark] = 
      arguments.getRecord(PARAM_CHART).get[String](PARAM_CHART_TYPE) match {
        case CHART_TYPE_BAR => Left(Vega.multiBarChart(
                                  data,
                                  schema(xaxis).name,
                                  yaxes.map { _._2 },
                                  ylabel = ""
                                ))
        case CHART_TYPE_AREA => Right(MarkArea)
        case CHART_TYPE_SCATTER => Right(MarkPoint)
        case CHART_TYPE_LINE_NO_POINTS => Right(MarkLine)
        case CHART_TYPE_LINE_POINTS => Right(MarkDef(
                                          `type` = MarkLine,
                                          point = Some(MarkDefPointAsBool(true))
                                        ))
      }

    val chart = 
      chartOrMark match {
        case Left(chart) => chart
        case Right(mark) => Vega.multiChart(
                                        mark,
                                        data,
                                        schema(xaxis).name,
                                        yaxes.map { _._2 },
                                        ylabel = ""
                                      )
      }
    chart.root.autosize = Some(AutosizeTypeFitX)

    val identifier =
      (name match {
        case "" => datasetName+"_by_"+schema(xaxis).name
        case _ => name
      }).replaceAll("[^a-zA-Z0-9]+", "_").toLowerCase

    context.chart(chart, identifier = identifier)

    // val schema = context.datasetSchema(datasetName).getOrElse {
    //   throw new VizierException(s"Unknown Dataset $datasetName")
    // }
    // context.chart(
    //   // Chart(
    //   //   dataset         = datasetName,
    //   //   name            = arguments.getOpt[String]("name").getOrElse { datasetName + "_Visualized" },
    //   //   chartType       = arguments.getRecord("chart").get[String]("chartType"),
    //   //   grouped         = arguments.getRecord("chart").getOpt[Boolean]("chartGrouped").getOrElse(true),
    //   //   xaxis           = arguments.getRecord("xaxis").getOpt[Int]("xaxis_column").map { schema(_).name },
    //   //   xaxisConstraint = arguments.getRecord("xaxis").getOpt[String]("xaxis_constraint"),
    //   //   series = arguments.getList("series").map { series => 
    //   //     ChartSeries(
    //   //       column     = schema(series.get[Int]("series_column")).name,
    //   //       label      = series.getOpt[String]("series_label"),
    //   //       constraint = series.getOpt[String]("series_constraint")
    //   //     )
    //   //   }
    //   )
    // )
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String]("dataset"))
      .andNothingElse

}

