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
import info.vizierdb.artifacts.VegaFrom
import info.vizierdb.artifacts.VegaChart
import info.vizierdb.artifacts.VegaScale
import info.vizierdb.artifacts.VegaScaleType
import info.vizierdb.artifacts.VegaAxis
import info.vizierdb.artifacts.VegaOrientation
import info.vizierdb.artifacts.VegaMarkEncoding
import info.vizierdb.artifacts.VegaMarkEncodingGroup
import info.vizierdb.artifacts.VegaAxisEncoding
import info.vizierdb.artifacts.VegaSignalEncoding
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

    // Figure out if we are being asked to emit a named artifact
    // Store the result in an option-type
    val artifactName = arguments.getOpt[String](PARAM_ARTIFACT)
                                .flatMap { case "" => None 
                                           case x => Some(x) }

    val nameComponents:Seq[(String, String, String)] = arguments.getList(PARAM_SERIES).map {
	series => 
		val datasetName = series.get[String](PARAM_DATASET)
		val xColIdx = series.get[Int](PARAM_X)
        	val yColIdx = series.get[Int](PARAM_Y)
		var dataset = context.dataframe(datasetName)
		val xCol = dataset.columns(xColIdx)
		val yCol = dataset.columns(yColIdx)
		(datasetName, yCol, xCol)
    }

    //Variables to hold the number of unique names for each naming convention
    val numberOfUniqueDatasetNames = nameComponents.map { c => c._1 }.toSet.size
    val numberOfUniqueYNames = nameComponents.map { c => c._2 }.toSet.size
    val numberOfUniqueXNames = nameComponents.map { c => c._3 }.toSet.size
    val numberOfUniqueDatasetandYNames = nameComponents.map { c => (c._1 + c._2) }.toSet.size
    val numberOfUniqueDatasetandXNames = nameComponents.map { c => (c._1 + c._3) }.toSet.size

    //lambda function to generate a series label based on uniqueness
    val makeLabel =
		if(numberOfUniqueDatasetNames == nameComponents.size) {
			(datasetName: String, yCol: String, xCol: String) => datasetName
	 	}
	  	else if(numberOfUniqueYNames == nameComponents.size) {
			(datasetName: String, yCol: String, xCol: String) => yCol
	   	}
	    	else if(numberOfUniqueXNames == nameComponents.size) {
			(datasetName: String, yCol: String, xCol: String) => xCol
	    	}
	    	else if(numberOfUniqueDatasetandYNames == nameComponents.size) {
			(datasetName: String, yCol: String, xCol: String) => datasetName + " " + yCol
	    	}
	    	else if(numberOfUniqueDatasetandXNames == nameComponents.size) {
			(datasetName: String, yCol: String, xCol: String) => datasetName + " " + xCol
	    	}
	    	else {
			(datasetName: String, yCol: String, xCol: String) => datasetName + " " + xCol + " " + yCol
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

	var headers = scala.collection.mutable.ListBuffer[String]()
	for (i <- 0 until dataset.columns.length) {
		headers += dataset.columns(i).toString
	}

        // If a filter is provided, apply it now
        series.getOpt[String](PARAM_FILTER) match {
          case None | Some("") => ()
          case Some(filter) => {
            dataset = dataset.filter(filter)
          }
        }

	var xIndex = 0
	var yIndex = 0
	var index = 0
	val listOfColumns:Seq[Column] = 
	  dataset.columns.map { columnName => 
	    if (columnName.toString == xCol) {
		xIndex = index
		index += 1
		dataset(columnName.toString).cast(DoubleType) as "x"
	    }
	    else if (columnName.toString == yCol) {
		yIndex = index
		index += 1
		dataset(columnName.toString).cast(DoubleType) as "y"
	    }
	    else {
		index += 1
		dataset(columnName.toString)
	    }
	  }
	dataset = dataset.select( listOfColumns:_* )	
	

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


        // And emit the series.
	var i = -1
        rows.map { row =>
	  i += 1
	  xValues += row.getAs[Double](xIndex)
	  yValues += row.getAs[Double](yIndex)
	  var signal = scala.collection.mutable.Map[String, JsValue]()
	  var j = 0
	  var tempRow = scala.collection.mutable.ListBuffer[String]()
	  for (i <- 0 until row.length) {
		tempRow += row(i).toString
	  }
	  tempRow.map { col =>
	  	signal += (headers(j) -> JsString(col))
	        j += 1
	  }
          Json.obj(
            "x" -> row.getAs[Double](xIndex),
            "y" -> row.getAs[Double](yIndex),
            "c" -> makeLabel(datasetName, yCol, xCol),
	    "s" -> JsObject(signal)
          )
        },
      }

    var minX = xValues.min
    var maxX = xValues.max
    var minY = yValues.min
    var maxY = yValues.max	

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
	    domainMin = Some(minX),
	    domainMax = Some(maxX)),
          
          // 'y': The y axis scale, mapping from data.y -> chart height
          VegaScale("y", VegaScaleType.Linear, 
            range = Some(VegaRange.Height),
            domain = Some(VegaDomain.Data(field = "y", data = "data")),
	    domainMin = Some(minY),
	    domainMax = Some(maxY)),

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
              fill = Some(VegaAxisEncoding(scale = "color", field = "c")),
	      tooltip = Some(VegaSignalEncoding(signal = "datum.s"))
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
