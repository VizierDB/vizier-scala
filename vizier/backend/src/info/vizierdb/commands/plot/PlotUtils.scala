package info.vizierdb.commands.plot

import info.vizierdb.util.StringUtils
import org.apache.spark.sql.DataFrame
import info.vizierdb.commands.ExecutionContext
import info.vizierdb.VizierException
import info.vizierdb.spark.SparkPrimitive
import info.vizierdb.artifacts.VegaData
import play.api.libs.json._
import info.vizierdb.artifacts.VegaMarkType
import info.vizierdb.artifacts.VegaMark
import info.vizierdb.artifacts.VegaFrom
import info.vizierdb.artifacts.VegaMarkEncodingGroup
import info.vizierdb.artifacts.VegaMarkEncoding
import info.vizierdb.artifacts.VegaValue
import org.apache.spark.sql.types.DoubleType

object PlotUtils
{
  val MAX_RECORDS = 10000

  case class Series(
    val dataset: String,
    val x: String,
    val y: String,
    val dataframe: DataFrame,
  )
  {
    // If we pull too many points, we're going to crash the client
    // so instead, what we're going to do is pull one more record
    // than we intend to display...
    val rows = dataframe.take(MAX_RECORDS+1)

    // And if we get all MAX+1 records, then bail out, letting the
    // user know that there's too much data to display.
    if(rows.size > MAX_RECORDS){
      throw new VizierException(
        s"$dataset has ${dataframe.count} rows, but chart cells are limited to $MAX_RECORDS rows.  Either summarize the data first, or use a python cell to plot the data."
      )
    }

    def vegaData(series: SeriesList): VegaData =
      VegaData(
        name = series.seriesName(this),
        values = 
          Some(rows.map { row =>
            JsObject(
              dataframe.schema.fields.zipWithIndex.map { case (field, idx) =>
                field.name -> SparkPrimitive.encode(row.get(idx), field.dataType)
              }.toMap
            )
          }.toSeq)
      )

    def minX = 
      rows.map { _.getAs[Double](x) }.min
    def maxX = 
      rows.map { _.getAs[Double](x) }.max
    def minY = 
      rows.map { _.getAs[Double](y) }.min
    def maxY = 
      rows.map { _.getAs[Double](y) }.max
  }

  def makeSeries(
    context: ExecutionContext,
    datasetName: String, 
    xIndex: Int, 
    yIndex: Int, 
    filter: Option[String],
    sort: Boolean = false
  ): Series =
  {
    var dataframe = context.dataframe(datasetName)

    // Apply the filter if provided
    filter match {
      case None | Some("") => ()
      case Some(filter) => {
        dataframe = dataframe.filter(filter)
      }
    }

    // Make sure the x and y columns are numeric
    dataframe = dataframe.select(
      dataframe.columns.zipWithIndex.map { case (col, idx) =>
        if(idx == xIndex || idx == yIndex){
          dataframe(col).cast(DoubleType)
        } else { dataframe(col) }
      }:_*
    )

    // Sort the data as appropriate
    if(sort){
      dataframe = dataframe.orderBy(
        dataframe.columns(xIndex),
      )
    }


    PlotUtils.Series(
      datasetName,
      dataframe.columns(xIndex),
      dataframe.columns(yIndex),
      dataframe,
    )
  }

  case class SeriesList(
    series: Seq[Series] 
  )
  {
    val size = series.size

    def uniqueDatasets = 
      series.map { _.dataset }.toSet
    def uniqueXAxes = 
      series.map { _.x }.toSet
    def uniqueYAxes = 
      series.map { _.y }.toSet

    def uniqueDatasetsAndXaxes =
      series.map { series => (series.dataset, series.x) }.toSet
    def uniqueDatasetsAndYaxes =
      series.map { series => (series.dataset, series.y) }.toSet
    def uniqueAxes = 
      series.map { series => (series.x, series.y) }.toSet

    /**
     * Generate a label for the provided series (dataset, x, y)
     */
    private val seriesLabel: Series => String =
      if(uniqueDatasets.size == size)             { series => series.dataset }
      else if(uniqueYAxes.size == size)           { series => series.y }
      else if(uniqueXAxes.size == size)           { series => series.x }
      else if(uniqueDatasetsAndYaxes.size == size){ series => series.dataset+"_"+series.y }
      else if(uniqueDatasetsAndXaxes.size == size){ series => series.dataset+"_"+series.x }
      else if(uniqueAxes.size == size)            { series => series.x+"_"+series.y }
      else                                        { series => series.dataset+"_"+series.x+"_"+series.y }

    def seriesName(series: Series): String = 
      seriesLabel(series)

    def seriesName(idx: Int): String = 
      seriesName(series(idx))

    def xAxis = 
      StringUtils.oxfordComma(uniqueXAxes.toSeq)
    def yAxis = 
      StringUtils.oxfordComma(uniqueYAxes.toSeq)

    def minX = 
      series.map { _.minX }.min
    def maxX = 
      series.map { _.maxX }.max
    def minY = 
      series.map { _.minY }.min
    def maxY = 
      series.map { _.maxY }.max

    def vegaData = 
      series.map { _.vegaData(this) }

    def names = 
      series.map { seriesName(_) }

    def simpleMarks(
      markType: VegaMarkType, 
      tooltip: Boolean = false,
      fill: Boolean = false,
    ) =
      series.map { data =>
        val name = seriesName(data)
        VegaMark(
          markType,
          from = Some(VegaFrom(data = name)),
          encode = Some(VegaMarkEncodingGroup(
            // 'enter' defines data in the initial state.
            enter = Some(VegaMarkEncoding(
              x = Some(VegaValue.Field(data.x).scale("x")),
              y = Some(VegaValue.Field(data.y).scale("y")),
              stroke = Some(VegaValue.Literal(JsString(name)).scale("color")),
              fill = 
                if(!fill){ None }
                else { Some(VegaValue.Literal(JsString(name)).scale("color")) },
              tooltip = 
                if(!tooltip){ None }
                else { Some(VegaValue.Signal("datum")) }
            ))
          ))
        )
      }
  }


}