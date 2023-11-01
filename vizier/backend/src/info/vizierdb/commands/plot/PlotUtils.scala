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
import info.vizierdb.artifacts.VegaValueReference
import info.vizierdb.artifacts.VegaTransform
import info.vizierdb.artifacts.VegaRegressionMethod
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.Column
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber

object PlotUtils
{
  val MAX_RECORDS = 10000
  val CDF_ATTR = "CDF"

  case class Series(
    val dataset: String,
    val x: String,
    val y: String,
    val dataframe: DataFrame,
    val regression: Option[VegaRegressionMethod] = None,
    val name: Option[String] = None,
    val sort: Boolean,
    val isBarChart: Boolean
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

    def vegaRegression(series: SeriesList): Option[VegaData] =
      regression.map { regression => 
        VegaData(
          name = series.seriesRegressionName(this),
          source = Some(Seq(series.seriesName(this))),
          transform = Some(Seq(
            VegaTransform.Regression(
              x = x,
              y = y,
              method = regression
            )
          ))
        )
      }

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
    sort: Boolean = false,
    regression: Option[VegaRegressionMethod] = None,
    name: Option[String] = None,
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
      dataset = datasetName,
      x = dataframe.columns(xIndex),
      y = dataframe.columns(yIndex),
      dataframe = dataframe,
      regression = regression,
      name = name
    )
  }

  def makeCDF(
    context: ExecutionContext,
    datasetName: String, 
    xIndex: Int, 
    filter: Option[String],
    name: Option[String] = None,
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

    val count = dataframe.count()

    // Make sure the x and y columns are numeric
    dataframe = dataframe.select(
      dataframe.columns.zipWithIndex.map { case (col, idx) =>
        if(idx == xIndex){
          dataframe(col).cast(DoubleType)
        } else { dataframe(col) }
      }:_*
    )

    // Sort the data as appropriate
    dataframe = dataframe.orderBy(
      dataframe.columns(xIndex),
    )

    val attrs = dataframe.schema.names.toSet
    val cdf_attr: String = 
      if(attrs contains CDF_ATTR){
        var i = 1
        while( attrs contains s"${CDF_ATTR}_$i" ){
          assert(i <= dataframe.schema.size)
          i += 1
        }
        s"${CDF_ATTR}_$i"
      } else { CDF_ATTR }

    dataframe = AnnotateWithSequenceNumber(dataframe, cdf_attr)

    dataframe = dataframe.select(
      dataframe.columns.map { col =>
        if(col == cdf_attr){ 
          (dataframe(col).cast(DoubleType) / count).as(cdf_attr)
        } else { dataframe(col) }
      }:_*
    )

    PlotUtils.Series(
      dataset = datasetName,
      x = dataframe.columns(xIndex),
      y = cdf_attr,
      dataframe = dataframe,
      regression = None,
      name = name,
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
      series.name.getOrElse { seriesLabel(series) }
    def seriesRegressionName(series: Series): String = 
      seriesName(series) + " [Trend]"
    def seriesName(idx: Int): String = 
      seriesName(series(idx))

    def xAxis = 
      StringUtils.oxfordComma(uniqueXAxes.toSeq)
    def yAxis = 
      StringUtils.oxfordComma(uniqueYAxes.toSeq)

    lazy val minX = 
      series.map { _.minX }.min
    lazy val maxX = 
      series.map { _.maxX }.max
    lazy val minY = 
      series.map { _.minY }.min
    lazy val maxY = 
      series.map { _.maxY }.max

    lazy val xDomainRequiresOffset =
      if(minX > 0){ 
        (maxX - minX) < minX/15
      }
      else {
        if(maxX > 0){ true }
        else {
          (maxX - minX) < (-maxX/15)
        }
      }
    lazy val yDomainRequiresOffset =
      if(minY > 0){ 
        (maxY - minY) < minY/15
      }
      else {
        if(maxY > 0){ true }
        else {
          (maxY - minY) < (-maxY/15)
        }
      }

    def domainMinX: Double = 
      if(xDomainRequiresOffset && minX > 0){ minX }
      else { 0 }

    def domainMaxX: Double = 
      if(xDomainRequiresOffset || maxX > 0){ maxX }
      else { 0 }

    def domainMinY: Double = 
      if(yDomainRequiresOffset && minY > 0){ minY }
      else { 0 }

    def domainMaxY: Double = 
      if(yDomainRequiresOffset || maxY > 0){ maxY }
      else { 0 }

    def vegaData = 
      series.map { _.vegaData(this) } ++
      series.flatMap { _.vegaRegression(this) }

    def names = 
      series.map { seriesName(_) }

    def simpleMarks(
      markType: VegaMarkType, 
      tooltip: Boolean = false,
      fill: Boolean = false,
      opacity: Double = 1.0
    ) =
      series.map { data =>
        val name = seriesName(data)
        VegaMark(
          markType,
          from = Some(VegaFrom(data = name)),
          encode = Some(VegaMarkEncodingGroup(
            // 'enter' defines data in the initial state.
            enter = Some(VegaMarkEncoding(
              x = Some(VegaValueReference.Field(data.x).scale("x")),
              y = Some(VegaValueReference.Field(data.y).scale("y")),
              stroke = Some(VegaValueReference.Literal(JsString(name)).scale("color")),
              fill = 
                if(!fill){ None }
                else { Some(VegaValueReference.Literal(JsString(name)).scale("color")) },
              tooltip = 
                if(!tooltip){ None }
                else { Some(VegaValueReference.Signal("datum")) },
              opacity = 
                if(opacity >= 1.0){ None }
                else { Some(opacity) }
            ))
          ))
        )
      } ++
      series.flatMap { data => 
        data.regression.map { _ => 
          VegaMark(
            VegaMarkType.Line,
            from = Some(VegaFrom(data = seriesRegressionName(data))),
            encode = Some(VegaMarkEncodingGroup(
              // 'enter' defines data in the initial state.
              enter = Some(VegaMarkEncoding(
                x = Some(VegaValueReference.Field(data.x).scale("x")),
                y = Some(VegaValueReference.Field(data.y).scale("y")),
                stroke = Some(VegaValueReference.Literal(JsString(seriesName(data))).scale("color")),
              ))
            ))
          )
        }
      }

  }


}