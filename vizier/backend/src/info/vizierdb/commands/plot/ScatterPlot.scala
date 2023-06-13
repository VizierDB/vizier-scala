package info.vizierdb.commands.plot

import org.apache.spark.sql.types._
import info.vizierdb.commands._
import info.vizierdb.viztrails.ProvenancePrediction
import play.api.libs.json.JsObject
import info.vizierdb.artifacts.VegaMark
import info.vizierdb.artifacts.VegaData
import info.vizierdb.artifacts.VegaMarkType
import play.api.libs.json.Json
import info.vizierdb.artifacts.VegaFrom
import info.vizierdb.artifacts.VegaChart
import info.vizierdb.artifacts.VegaScale
import info.vizierdb.artifacts.VegaScaleType
import info.vizierdb.artifacts.VegaAxis
import info.vizierdb.artifacts.VegaOrientation
import info.vizierdb.artifacts.VegaMarkEncoding
import info.vizierdb.artifacts.VegaMarkEncodingGroup
import info.vizierdb.artifacts.VegaAxisEncoding
import info.vizierdb.artifacts.VegaDomain
import info.vizierdb.artifacts.VegaRange
import info.vizierdb.artifacts.VegaAutosize
import info.vizierdb.artifacts.VegaPadding
import info.vizierdb.artifacts.VegaLegend
import info.vizierdb.artifacts.VegaLegendType


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
    //Variables to store axes titles
    var xTitle = ""
    var yTitle = ""

    //Variables to hold the x and y values
    var xValues = scala.collection.mutable.ListBuffer[Double]()
    var yValues = scala.collection.mutable.ListBuffer[Double]()

    //Variables to hold all of the dataset names, y columns, x columns, and combinations of them along with boolean values that can be used to identify if those naming conventions create all unique names
    var datasetNames = scala.collection.mutable.Map[String, Int]()
    var datasetUnique = true
    var yColumns = scala.collection.mutable.Map[String, Int]()
    var yUnique = true
    var xColumns = scala.collection.mutable.Map[String, Int]()
    var xUnique = true
    var datasetAndY = scala.collection.mutable.Map[String, Int]()
    var datasetAndYUnique = true
    var datasetAndX = scala.collection.mutable.Map[String, Int]()
    var datasetAndXUnique = true

    // Figure out if we are being asked to emit a named artifact
    // Store the result in an option-type
    val artifactName = arguments.getOpt[String](PARAM_ARTIFACT)
                                .flatMap { case "" => None 
                                           case x => Some(x) }

    

    //meaningless series object that's just here to iterate through all of the dataset names, x axes, and y axes so we can check for uniqueness
    var tempSeries = 
      // For each series we're asked to generate...
      arguments.getList(PARAM_SERIES)
               .map { series => 

        // Extract the dataset artifact and figure out the 
        // x and y columns.
        // 
        // Remember: ColIdParameter parameters give us an 
        // integer column index.
        val datasetName = series.get[String](PARAM_DATASET)
        if (datasetNames.contains(datasetName)) {
	  datasetNames(datasetName) += 1
	  datasetUnique = false
	}
	else {
	  datasetNames(datasetName) = 1
	}
        val xColIdx = series.get[Int](PARAM_X)
        val yColIdx = series.get[Int](PARAM_Y)
        // Store dataset as a var to allow transformations
        // below.
        var dataset = context.dataframe(datasetName)
        val xCol = dataset.columns(xColIdx)
        if (xColumns.contains(xCol)) {
	  xColumns(xCol) += 1
	  xUnique = false
	}
	else {
	  xColumns(xCol) = 1
	}
	if (datasetAndX.contains((datasetName + xCol))) {
	  datasetAndX((datasetName + xCol)) += 1
	  datasetAndXUnique = false
	}
	else {
	  datasetAndX((datasetName + xCol)) = 1
	}
        val yCol = dataset.columns(yColIdx)
        if (yColumns.contains(yCol)) {
	  yColumns(yCol) += 1
	  yUnique = false
	}
	else {
	  yColumns(yCol) = 1
	}
	if (datasetAndY.contains((datasetName + yCol))) {
	  datasetAndY((datasetName + yCol)) += 1
	  datasetAndYUnique = false
	}
	else {
	  datasetAndY((datasetName + yCol)) = 1
	}
      }
      



/**
     * A Seq[Seq[JsObject]]; Each inner Seq is a single series
     * with the JsObjects being tuples: x, y, c (c is the series
     * label for the legend).
     */
    val series = 
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
	xTitle = xCol
	yTitle = yCol

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
        // crash, since the types can't be plotted in a scatter plot
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

        //Assign series label based on priority order
	var makeLabel = () =>
	if(datasetUnique) { 
	  datasetName
	} else if (yUnique) {
          yCol
	} else if (xUnique) {
          xCol
	} else if (datasetAndYUnique) {
          datasetName + " " + yCol
	} else if (datasetAndXUnique) {
          datasetName + " " + xCol
	} else {
          datasetName + " " + xCol + " " + yCol
	}
	val seriesLabel = makeLabel()


		     

        // And emit the series.
        rows.map { row =>
	  xValues += row.getAs[Double](0)
	  yValues += row.getAs[Double](1)
          Json.obj(
            "x" -> row.getAs[Double](0),
            "y" -> row.getAs[Double](1),
            "c" -> seriesLabel
          )
        },
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
        data = Seq(VegaData("data",
          values = Some(series.flatten)
        )),

        // Let vega know how to map data values to plot features
        scales = Seq(
          // 'x': The x axis scale, mapping from data.x -> chart width
          VegaScale("x", VegaScaleType.Linear, 
            range = Some(VegaRange.Width),
            domain = Some(VegaDomain.Data(field = "x", data = "data")),
	    domainMin = Some(xValues.min),
	    domainMax = Some(xValues.max)),
          
          // 'y': The y axis scale, mapping from data.y -> chart height
          VegaScale("y", VegaScaleType.Linear, 
            range = Some(VegaRange.Height),
            domain = Some(VegaDomain.Data(field = "y", data = "data")),
	    domainMin = Some(yValues.min),
	    domainMax = Some(yValues.max)),

          // 'color': The color scale, mapping from data.c -> color category
          VegaScale("color", VegaScaleType.Ordinal,
            range = Some(VegaRange.Category),
            domain = Some(VegaDomain.Data(field = "c", data = "data"))),
        ),

        // Define the chart axes (based on the 'x' and 'y' scales)
        axes = Seq(
          VegaAxis("x", VegaOrientation.Bottom, ticks = Some(true), title = Some(xTitle)),
          VegaAxis("y", VegaOrientation.Left, ticks = Some(true), title = Some(yTitle))
        ),

        // Actually define the circles.  There's a single mark here
        // that generates one circle per data point (based on the stroke 
        // encoding)
        marks = Seq(VegaMark(
          VegaMarkType.Symbol,
          from = Some(VegaFrom(data = "data")),
          encode = Some(VegaMarkEncodingGroup(
            // 'enter' defines data in the initial state.
            enter = Some(VegaMarkEncoding(
              x = Some(VegaAxisEncoding(scale = "x", field = "x")),
              y = Some(VegaAxisEncoding(scale = "y", field = "y")),
              fill = Some(VegaAxisEncoding(scale = "color", field = "c"))
            ))
          ))
        )),
	

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
