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
        "Line Chart"   -> "Line Chart",
        "Scatter Plot" -> "Scatter Plot"
      ), default = Some(1)),
      BooleanParameter(id = "chartGrouped", name = "Grouped", required = false),
    ))
  )
  def format(arguments: Arguments): String = 
    s"CREATE ${arguments.getRecord("chart").get[String]("chartType")} ${arguments.pretty("name")} FOR ${arguments.pretty("dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val schema = context.datasetSchema(datasetName).getOrElse {
      throw new VizierException(s"Unknown Dataset $datasetName")
    }
    context.chart(
      Chart(
        dataset         = datasetName,
        name            = arguments.getOpt[String]("name").getOrElse { datasetName },
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
    context.message("Dataset Cloned")
  }
}

