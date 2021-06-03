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

import play.api.libs.json._
import org.mimirdb.api.{ Tuple => MimirTuple, MimirAPI }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.types.ArtifactType
import info.vizierdb.artifacts.{ Chart, ChartSeries }
import org.apache.spark.sql.{ DataFrame, Row }
import org.mimirdb.api.request.ResultTooBig
import org.mimirdb.caveats.implicits._
import org.mimirdb.api.request.Query
import org.apache.spark.unsafe.types.UTF8String
import info.vizierdb.VizierException

object SimpleChart extends Command
{
  def name: String = "Simple Chart"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    StringParameter(id = "name", name = "Chart Name", required = false),
    ListParameter(id = "series", name = "Data Series", components = Seq(
      ColIdParameter(id = "series_column", name = "Column"),
      StringParameter(id = "series_constraint", name = "Constraint", required = false),
      StringParameter(id = "series_label", name = "Label", required = false),
    )),
    RecordParameter(id = "xaxis", name = "X Axis", components = Seq(
      ColIdParameter(id = "xaxis_column", name = "Column", required = false),
      StringParameter(id = "xaxis_constraint", name = "Constraint", required = false),
    )),
    RecordParameter(id = "chart", name = "Chart", components = Seq(
      EnumerableParameter(id = "chartType", name = "Type", values = EnumerableValue.withNames(
        "Area Chart"   -> "Area Chart",
        "Bar Chart"    -> "Bar Chart",
        "Line Chart with Points"   -> "Line Chart with Points",
        "Line Chart without Points"   -> "Line Chart without Points",
        "Scatter Plot" -> "Scatter Plot"
      ), default = Some(1)),
      BooleanParameter(id = "chartGrouped", name = "Grouped", required = false, default = Some(true)),
    ))
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.getRecord("chart").get[String]("chartType")} ${arguments.pretty("name")} FOR ${arguments.pretty("dataset")}"
  def title(arguments: Arguments): String = 
    s"${arguments.getRecord("chart").get[String]("chartType")} of ${arguments.pretty("dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val schema = context.datasetSchema(datasetName).getOrElse {
      throw new VizierException(s"Unknown Dataset $datasetName")
    }
    context.chart(
      Chart(
        dataset         = datasetName,
        name            = arguments.getOpt[String]("name").getOrElse { datasetName + "_Visualized" },
        chartType       = arguments.getRecord("chart").get[String]("chartType"),
        grouped         = arguments.getRecord("chart").getOpt[Boolean]("chartGrouped").getOrElse(true),
        xaxis           = arguments.getRecord("xaxis").getOpt[Int]("xaxis_column").map { schema(_).name },
        xaxisConstraint = arguments.getRecord("xaxis").getOpt[String]("xaxis_constraint"),
        series = arguments.getList("series").map { series => 
          ChartSeries(
            column     = schema(series.get[Int]("series_column")).name,
            label      = series.getOpt[String]("series_label"),
            constraint = series.getOpt[String]("series_constraint")
          )
        }
      )
    )
  }

  def predictProvenance(arguments: Arguments) = 
    Some( (Seq(arguments.get[String]("dataset")), 
           Seq.empty) )

}

