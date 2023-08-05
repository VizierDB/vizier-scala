package info.vizierdb.commands.plot

import org.apache.spark.sql.types._
import info.vizierdb.commands._
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json._
import info.vizierdb.artifacts.VegaMark
import info.vizierdb.artifacts.VegaData
import info.vizierdb.artifacts.VegaMarkType
import info.vizierdb.artifacts.VegaFrom
import info.vizierdb.artifacts.VegaChart
import info.vizierdb.artifacts.VegaScale
import info.vizierdb.artifacts.VegaScaleType
import info.vizierdb.artifacts.VegaAxis
import info.vizierdb.artifacts.VegaOrientation
import info.vizierdb.artifacts.VegaMarkEncoding
import info.vizierdb.artifacts.VegaMarkEncodingGroup
import info.vizierdb.artifacts.VegaValue
import info.vizierdb.artifacts.VegaDomain
import info.vizierdb.artifacts.VegaRange
import info.vizierdb.artifacts.VegaAutosize
import info.vizierdb.artifacts.VegaPadding
import info.vizierdb.artifacts.VegaLegend
import info.vizierdb.artifacts.VegaLegendType

object LineChart extends Command
{
  val PARAM_SERIES = "series" 
  val PARAM_DATASET = "dataset"
  val PARAM_X = "xcol"
  val PARAM_Y = "ycol"
  val PARAM_FILTER = "filter"
  val PARAM_COLOR = "color"
  val PARAM_LABEL = "label"
  val PARAM_ARTIFACT = "artifact"

  val MAX_RECORDS = 10000

  override def name: String = "Line Chart"

  override def parameters: Seq[Parameter] = Seq(
    ListParameter(id = PARAM_SERIES, name = "Lines", components = Seq(
      DatasetParameter(id = PARAM_DATASET, name = "Dataset"),
      ColIdParameter(id = PARAM_X, name = "X-axis"),
      ColIdParameter(id = PARAM_Y, name = "Y-axis"),
      StringParameter(id = PARAM_LABEL, name = "Label", required = false),
      StringParameter(id = PARAM_FILTER, name = "Filter", required = false),
      StringParameter(id = PARAM_COLOR, name = "Color", required = false),
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

    /**
     * A Seq[Seq[JsObject]]; Each inner Seq is a single series
     * with the JsObjects being tuples: x, y, c (c is the series
     * label for the legend).
     */
    var series: Seq[VegaData] = 
      // For each series we're asked to generate...
      arguments.getList(PARAM_SERIES)
               .map { series => 

        // Extract the dataset artifact and figure out the 
        // x and y columns.
        // 
        // Remember: ColIdParameter parameters give us an 
        // integer column index.
        val datasetName = series.get[String](PARAM_DATASET)
        val xColIdx = series.get[Int](PARAM_X)
        val yColIdx = series.get[Int](PARAM_Y)
        // Store dataset as a var to allow transformations
        // below.
        var dataset = context.dataframe(datasetName)
        val xCol = dataset.columns(xColIdx)
        val yCol = dataset.columns(yColIdx)

        // If a filter is provided, apply it now
        series.getOpt[String](PARAM_FILTER) match {
          case None | Some("") => ()
          case Some(filter) => {
            dataset = dataset.filter(filter)
          }
        }

        // Pull out the x and y fields.  Cast them to doubles
        // for easier extraction.
        // Note: if they can't be cast, it's actually fine to
        // crash, since the types can't be plotted in a line chart
        dataset = dataset.select(
          dataset(xCol).cast(DoubleType) as "x",
          dataset(yCol).cast(DoubleType) as "y"
        )

        // Sort the data as appropriate
        dataset = dataset.orderBy("x")

        // If we pull too many points, we're going to crash the client
        // so instead, what we're going to do is pull one more record
        // than we intend to display...
        val rows = dataset.take(MAX_RECORDS+1)

        // And if we get all MAX+1 records, then bail out, letting the
        // user know that there's too much data to display.
        if(rows.size > MAX_RECORDS){
          context.error(s"$datasetName has ${dataset.count} rows, but chart cells are limited to $MAX_RECORDS rows.  Either summarize the data first, or use a python cell to plot the data.")
          return
        }

        // Pull out the series label, or heuristically come up with
        // a better one if necessary...
        //
        // We could probably do something more clever here...
        // perhaps e.g., see which of xCol, yCol and datasetName are
        // unique across all series.
        // For now, let's do a simple hack...
        val seriesLabel = 
          series.getOpt[String](PARAM_LABEL)
                .flatMap { case "" => None; case x => Some(x) }
                .getOrElse { yCol }

        // And emit the series.
        VegaData(
          name = seriesLabel,
          values = 
            Some(rows.map { row =>
              Json.obj(
                "x" -> row.getAs[Double](0),
                "y" -> row.getAs[Double](1),
              )
            }.toSeq)
        )
      }

    context.vega(
      VegaChart(
        description = "",

        // 600x400px chart, scaling as needed
        width = 600,
        height = 400,
        autosize = VegaAutosize.Fit,

        // 10 extra pixels around the border
        padding = VegaPadding.all(10),

        // Each data value is already annotated with the series 
        // label, so just combine (flatten) all the series' data
        // together into a single big dataset (named "data")
        data = series ++ Seq(
                VegaData(
                  name = "_vizier_overview",
                  source = Some(series.map { _.name })
                )
              ),

        // Let vega know how to map data values to plot features
        scales = Seq(
          // 'x': The x axis scale, mapping from data.x -> chart width
          VegaScale("x", VegaScaleType.Linear, 
            range = Some(VegaRange.Width),
            domain = Some(VegaDomain.Data(field = "x", data = "_vizier_overview"))),
          
          // 'y': The y axis scale, mapping from data.y -> chart height
          VegaScale("y", VegaScaleType.Linear, 
            range = Some(VegaRange.Height),
            domain = Some(VegaDomain.Data(field = "y", data = "_vizier_overview"))),

          // 'color': The color scale, mapping from data.c -> color category
          VegaScale("color", VegaScaleType.Ordinal,
            range = Some(VegaRange.Category),
            domain = Some(VegaDomain.Literal(
                        series.map { data => JsString(data.name) }
                    )))
        ),

        // Define the chart axes (based on the 'x' and 'y' scales)
        axes = Seq(
          VegaAxis("x", VegaOrientation.Bottom, ticks = Some(true)),
          VegaAxis("y", VegaOrientation.Left, ticks = Some(true))
        ),

        // Actually define the line(s).  There's a single mark here
        // that generates one line per color (based on the stroke 
        // encoding)
        marks = 
          series.map { data => 
            VegaMark(
              VegaMarkType.Line,
              from = Some(VegaFrom(data = data.name)),
              encode = Some(VegaMarkEncodingGroup(
                // 'enter' defines data in the initial state.
                enter = Some(VegaMarkEncoding(
                  x = Some(VegaValue.Field("x").scale("x")),
                  y = Some(VegaValue.Field("y").scale("y")),
                  stroke = Some(VegaValue.Literal(JsString(data.name)).scale("color"))
                ))
              ))
            )
          },

        // Finally ensure that there is a legend displayed
        legends = Seq(
          VegaLegend(
            VegaLegendType.Symbol,
            stroke = Some("color")
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