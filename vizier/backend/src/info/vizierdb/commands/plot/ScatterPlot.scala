package info.vizierdb.commands.plot

import org.apache.spark.sql.types._
import org.apache.spark.sql.Column
import info.vizierdb.commands._
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json.JsObject
import info.vizierdb.artifacts.VegaMark
import info.vizierdb.artifacts.VegaData
import info.vizierdb.artifacts.VegaMarkType
import play.api.libs.json._
import info.vizierdb.artifacts.VegaTransform
import info.vizierdb.artifacts.VegaFrom
import info.vizierdb.artifacts.VegaChart
import info.vizierdb.artifacts.VegaScale
import info.vizierdb.artifacts.VegaScaleType
import info.vizierdb.artifacts.VegaAxis
import info.vizierdb.artifacts.VegaOrientation
import info.vizierdb.artifacts.VegaMarkEncoding
import info.vizierdb.artifacts.VegaMarkEncodingGroup
import info.vizierdb.artifacts.VegaValue
import info.vizierdb.artifacts.VegaSignalEncoding
import info.vizierdb.artifacts.VegaDomain
import info.vizierdb.artifacts.VegaRange
import info.vizierdb.artifacts.VegaAutosize
import info.vizierdb.artifacts.VegaPadding
import info.vizierdb.artifacts.VegaLegend
import info.vizierdb.artifacts.VegaLegendType
import info.vizierdb.artifacts.VegaRegressionMethod
import play.api.libs.json._


object ScatterPlot extends Command
{
  val PARAM_SERIES = "series" 
  val PARAM_DATASET = "dataset"
  val PARAM_X = "xcol"
  val PARAM_Y = "ycol"
  val PARAM_FILTER = "filter"
  val PARAM_COLOR = "color"
  val PARAM_LABEL = "label"
  val PARAM_ARTIFACT = "artifact"
  val PARAM_REGRESSION = "regression"

  val MAX_RECORDS = 10000

  override def name: String = "Scatter Plot"

  override def parameters: Seq[Parameter] = Seq(
    ListParameter(id = PARAM_SERIES, name = "Lines", components = Seq(
      DatasetParameter(id = PARAM_DATASET, name = "Dataset"),
      ColIdParameter(id = PARAM_X, name = "X-axis"),
      ColIdParameter(id = PARAM_Y, name = "Y-axis"),
      StringParameter(id = PARAM_LABEL, name = "Label", required = false),
      StringParameter(id = PARAM_FILTER, name = "Filter", required = false),
      StringParameter(id = PARAM_COLOR, name = "Color", required = false),
      EnumerableParameter(id = PARAM_REGRESSION, name = "Regression", required = false, values = EnumerableValue.withNames(
        "---"         -> "",
        "Linear"      -> VegaRegressionMethod.Linear.key,
        "Logarithmic" -> VegaRegressionMethod.Logarithmic.key,
        "Exponential" -> VegaRegressionMethod.Exponential.key,
        "Power"       -> VegaRegressionMethod.Power.key,
        "Quadratic"   -> VegaRegressionMethod.Quadratic.key,
      ))
    )),
    StringParameter(id = PARAM_ARTIFACT, name = "Output Artifact (blank to show only)", required = false)
  )
  override def title(arguments: Arguments): String = 
    "PLOT "+arguments.getList(PARAM_SERIES).map { series =>
      series.pretty(PARAM_DATASET)
    }.toSet.mkString(", ")

  override def format(arguments: Arguments): String = 
    "PLOT "+arguments.getList(PARAM_SERIES).map { series =>
      f"${series.pretty(PARAM_DATASET)}.{${series.pretty(PARAM_X)}, ${series.pretty(PARAM_Y)}}${series.getOpt[String](PARAM_LABEL).map { " AS " + _ }.getOrElse("")}"
    }.mkString("\n     ")

  override def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {

    // Figure out if we are being asked to emit a named artifact
    // Store the result in an option-type
    val artifactName = arguments.getOpt[String](PARAM_ARTIFACT)
                                .flatMap { case "" => None 
                                           case x => Some(x) }

    // Feed the configuration into PlotUtils
    val series =
      PlotUtils.SeriesList( 
        arguments.getList(PARAM_SERIES).map { series => 
          PlotUtils.makeSeries(
            context     = context,
            datasetName = series.get[String](PARAM_DATASET),
            xIndex      = series.get[Int](PARAM_X),
            yIndex      = series.get[Int](PARAM_Y),
            filter      = series.getOpt[String](PARAM_FILTER),
            regression  = series.getOpt[String](PARAM_REGRESSION).flatMap { VegaRegressionMethod(_) }
          )
        }
      )

    context.vega(
      VegaChart(
        description = "",

        // 600x400px chart, scaling as needed
        width = 600,
        height = 400,
        autosize = VegaAutosize.Fit,

        // 10 extra pixels around the border
        padding = VegaPadding.all(10),

        // Rely on PlotUtils to pick these out
        data = series.vegaData,

        // Let vega know how to map data values to plot features
        scales = Seq(
          // 'x': The x axis scale, mapping from data.x -> chart width
          VegaScale("x", VegaScaleType.Linear, 
            range = Some(VegaRange.Width),
            domain = Some(VegaDomain.Literal(Seq(
              JsNumber(series.minX),
              JsNumber(series.maxX)
            )))),
          
          // 'y': The y axis scale, mapping from data.y -> chart height
          VegaScale("y", VegaScaleType.Linear, 
            range = Some(VegaRange.Height),
            domain = Some(VegaDomain.Literal(Seq(
              JsNumber(series.minY),
              JsNumber(series.maxY)
            )))),

          // 'color': The color scale, mapping from data.c -> color category
          VegaScale("color", VegaScaleType.Ordinal,
            range = Some(VegaRange.Category),
            domain = Some(VegaDomain.Literal(series.names.map { JsString(_) })))
        ),

        // Define the chart axes (based on the 'x' and 'y' scales)
        axes = Seq(
          VegaAxis("x", VegaOrientation.Bottom, ticks = Some(true),
                   title = Some(series.xAxis)),
          VegaAxis("y", VegaOrientation.Left, ticks = Some(true),
                   title = Some(series.yAxis)),
        ),

        // Actually define the circles.  There's a single mark here
        // that generates one circle per data point (based on the stroke 
        // encoding)
        marks = 
          series.simpleMarks(
            VegaMarkType.Symbol,
            tooltip = true,
            fill = true,
            opacity = 0.7,
          ),
	
        // Finally ensure that there is a legend displayed
        legends = Seq(
          VegaLegend(
            VegaLegendType.Symbol,
            stroke = Some("color"),
      	    fill = Some("color")
          )
        )
      ),
      identifier = artifactName.getOrElse(null),
      withMessage = true
    )
  }

  override def predictProvenance(arguments: Arguments, properties: JsObject): ProvenancePrediction =
    ProvenancePrediction
      .definitelyReads(
        arguments.getList(PARAM_SERIES)
                 .map { _.get[String](PARAM_DATASET) }
                 .toSet.toSeq:_*
      )
      .andNothingElse

}