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

import info.vizierdb.util.StringUtils
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import info.vizierdb.commands.ExecutionContext
import info.vizierdb.VizierException
import info.vizierdb.spark.SparkPrimitive
import info.vizierdb.artifacts.VegaData
import play.api.libs.json._
import info.vizierdb.artifacts.VegaMarkType
import info.vizierdb.artifacts.VegaMark
import info.vizierdb.artifacts.VegaFacet
import info.vizierdb.artifacts.VegaFrom
import info.vizierdb.artifacts.VegaMarkEncodingGroup
import info.vizierdb.artifacts.VegaMarkEncoding
import info.vizierdb.artifacts.VegaValueReference
import info.vizierdb.artifacts.VegaTransform
import info.vizierdb.artifacts.VegaRegressionMethod
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.Column
import collection.JavaConverters._
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.types.DataType
import info.vizierdb.spark.rowids.AnnotateWithSequenceNumber

object PlotUtils {
  val MAX_RECORDS = 10000
  val CDF_ATTR = "CDF"

  case class Series(
      val dataset: String,
      val x: String,
      val y: Seq[String],
      val dataframe: DataFrame,
      val regression: Option[VegaRegressionMethod] = None,
      var name: String = null,
      var index: Int = -1,
      var color: String
  ) {
    // If we pull too many points, we're going to crash the client
    // so instead, what we're going to do is pull one more record
    // than we intend to display...
    lazy val rows = {
      val rows = dataframe.take(MAX_RECORDS + 1)

      // And if we get all MAX+1 records, then bail out, letting the
      // user know that there's too much data to display.
      if (rows.size > MAX_RECORDS) {
        throw new VizierException(
          s"$dataset has ${dataframe.count} rows, but chart cells are limited to $MAX_RECORDS rows.  Either summarize the data first, or use a python cell to plot the data."
        )
      }
      /* return */
      rows
    }

    def regressionName = name + " [Trend]"

    /** Retrieve the [VegaData] object encoding this series
      * @return
      *   The [VegaData] object encoding this series
      */
    println(regressionName)
    def vegaData: VegaData =
      VegaData(
        name = name,
        values = Some(rows.map { row =>
          JsObject(
            dataframe.schema.fields.zipWithIndex.map { case (field, idx) =>
              field.name -> SparkPrimitive.encode(row.get(idx), field.dataType)
            }.toMap
          )
        }.toSeq)
      )
      println(vegaData)

    /** Retrieve the [VegaData] object encoding a regression over this series
      * @return
      *   The [VegaData] object encoding a regression over this series
      */
    def vegaRegression: Option[Seq[VegaData]] =
      regression.flatMap { regressionMethod =>
        Some(y.map { yVal =>
          VegaData(
            name = regressionName,
            source = Some(Seq(name)),
            transform = Some(
              Seq(
                VegaTransform.Regression(
                  x = x,
                  y = yVal,
                  method = regressionMethod
                )
              )
            )
          )
        })
      }
    

    /** Transform the series by aggregating the y-axis value.
      * @param aggFn
      *   The aggregate function to use (sum by default)
      * @return
      *   A new [Series] object with the y-axis value aggregated
      */
    def aggregated(aggFn: Column => Column = sum): Series = {
      val aggExprs = dataframe.columns.flatMap {
        case colName if colName == y =>
          // For the column to sum, use the 'sum' aggregation.
          Some(aggFn(dataframe(colName)).as(colName))
        case colName if colName == x =>
          // For the groupBy column, we do not need an aggregation expression.
          None
        case colName =>
          // For all other columns, preserve the first entry.
          Some(first(colName).as(colName))
      }
      // Apply the aggregation expressions to the DataFrame.
      val aggDataframe =
        dataframe
          .groupBy(x)
          .agg(aggExprs.head, aggExprs.tail: _*)

      copy(
        dataframe = aggDataframe
      )
    }

    /** Transform the series by sorting on the x axis
      * @return
      *   The Series object, but sorted on the x-axis
      */
    def sorted: Series = {
      copy(
        dataframe = dataframe.sort(dataframe(x))
      )
    }

    /** Transform the series by applying the provided filter
      * @return
      *   The Series object, but sorted on the x-axis
      */
    def filtered(filter: String): Series = {
      if (filter == "") { return this }
      else {
        copy(
          dataframe = dataframe.filter(filter)
        )
      }
    }

    def cdf: Series = {
      val count = dataframe.count()

      val attrs = dataframe.schema.names.toSet
      val cdf_attr: String =
        if (attrs contains CDF_ATTR) {
          var i = 1
          while (attrs contains s"${CDF_ATTR}_$i") {
            assert(i <= dataframe.schema.size)
            i += 1
          }
          s"${CDF_ATTR}_$i"
        } else { CDF_ATTR }

      var newDataframe = dataframe.orderBy(x)

      newDataframe = AnnotateWithSequenceNumber(newDataframe, cdf_attr)

      newDataframe = newDataframe.select(
        newDataframe.columns.map { col =>
          if (col == cdf_attr) {
            (newDataframe(col).cast(DoubleType) / count).as(cdf_attr)
          } else { newDataframe(col) }
        }: _*
      )

      copy(
        dataframe = newDataframe,
        y = Seq(cdf_attr),
        x = cdf_attr
      )
    }

    def distinctX: Set[Any] =
      rows.map { _.getAs[Any](x) }.toSeq.toSet

    def minX =
      rows.map { _.getAs[Double](x) }.min
    def maxX =
      rows.map { _.getAs[Double](x) }.max
    def minY: Double =
      y.map(yCol => rows.map(_.getAs[Double](yCol)).min).min
    def maxY: Double =
      y.map(yCol => rows.map(_.getAs[Double](yCol)).max).max
  }

  def makeSeries(
      context: ExecutionContext,
      datasetName: String,
      xIndex: Int,
      yIndex: Seq[Int],
      xDataType: DataType = DoubleType,
      yDataType: DataType = DoubleType,
      castToNumeric: Set[Int] = null,
      regression: Option[VegaRegressionMethod] = None,
      name: Option[String] = None,
      color: Option[String] = None
  ): Series = {
    var dataframe = context.dataframe(datasetName)

    // Make sure the relevant columns are numeric
    dataframe = dataframe.select(
      dataframe.columns.zipWithIndex.map { case (col, idx) =>
        if (idx == xIndex) {
          dataframe(col).cast(xDataType)
        } else if (yIndex.contains(idx)) {
          dataframe(col).cast(yDataType)
        } else {
          dataframe(col)
        }
      }: _*
    )

    PlotUtils.Series(
      dataset = datasetName,
      x = dataframe.columns(xIndex),
      y = yIndex.map(dataframe.columns(_)),
      dataframe = dataframe,
      regression = regression,
      name = name.getOrElse(null),
      color = color.getOrElse("#0000FF")
    )
  }

  /** A list of [Series] objects
    *
    * Note: A [Series] object may only live in one series. Adding one to a
    * SeriesList mutates the series: (i) identifying its position in the series,
    * and (ii) ensuring that the series object has a name. Both of these are
    * handled as part of the SeriesList constructor.
    */
  case class SeriesList(
      series: Seq[Series]
  ) {
    ////////////////////// General Variables
    val size = series.size

    ////////////////////// Default Name Construction

    /** Compute the set of distinct datasets represented in all series
      */
    def uniqueDatasets =
      series.map { _.dataset }.toSet

    /** Compute the set of unique x-axis labels represented in all series
      */
    def uniqueXAxes =
      series.map { _.x }.toSet

    /** Compute the set of unique y-axis labels represented in all series
      */
    def uniqueYAxes =
      series.flatMap { _.y }.toSet

    /** Compute the set of unique dataset + x-axis label pairs represented in
      * all series
      */
    def uniqueDatasetsAndXaxes =
      series.map { series => (series.dataset, series.x) }.toSet

    /** Compute the set of unique dataset + y-axis label pairs represented in
      * all series
      */
    def uniqueDatasetsAndYaxes =
      series.map { series => (series.dataset, series.y) }.toSet

    /** Compute the set of unique x- and y-axis label pairs represented in all
      * series
      */
    def uniqueAxes =
      series.map { series => (series.x, series.y) }.toSet

    /** Compute the set of unique dataset + x- and y-axis label pairs
      * represented in all series
      */
    def uniqueDatasetsAndAxes =
      series.map { series => (series.dataset, series.x, series.y) }.toSet

    ////////////////////////////////////////////////
    // INITIALIZING SERIES STEP 1: Assign the index field.
    ////////////////////////////////////////////////
    for ((s, idx) <- series.zipWithIndex) { s.index = idx }
    ////////////////////////////////////////////////

    /** A function for computing a guaranteed unique label for each series
      * represented in this series list.
      *
      * This function is marked as private because we allow
      */
    private val seriesLabel: Series => String =
      if (uniqueDatasets.size == size) { series => series.dataset }
      else if (uniqueYAxes.size == size) { series => series.y.mkString("_") }
      else if (uniqueXAxes.size == size) { series => series.x }
      else if (uniqueDatasetsAndYaxes.size == size) { series =>
        series.dataset + "_" + series.y
      } else if (uniqueDatasetsAndXaxes.size == size) { series =>
        series.dataset + "_" + series.x
      } else if (uniqueAxes.size == size) { series =>
        series.x + "_" + series.y
      } else if (uniqueDatasetsAndAxes.size == size) { series =>
        series.dataset + "_" + series.x + "_" + series.y
      } else { series =>
        series.dataset + "_" + series.x + "_" + series.y + "_" + series.index
      }

    ////////////////////////////////////////////////
    // INITIALIZING SERIES STEP 2: Ensure the presence of a name
    ////////////////////////////////////////////////
    for (s <- series) { if (s.name == null) { s.name = seriesLabel(s) } }
    ////////////////////////////////////////////////

    ////////////////////// Axis labeling and bounds

    /** Generate a unique label for the x-axis
      */
    def xAxis =
      StringUtils.oxfordComma(uniqueXAxes.toSeq)

    /** Generate a unique label for the y-axis
      */
    def yAxis =
      StringUtils.oxfordComma(uniqueYAxes.toSeq)

    /** Lower bound for the x-axis
      *
      * Note: This value assumes that the x-axis is numeric; It is lazy to avoid
      * computing it when the x-axis is **not** numeric. Accessing this value
      * for e.g., a bar chart, where the x-axis values are strings will trigger
      * a runtime error. You probably want [uniqueXValues] instead.
      */
    lazy val minX =
      series.map { _.minX }.min

    /** Upper bound for the x-axis
      *
      * Note: This value assumes that the x-axis is numeric; It is lazy to avoid
      * computing it when the x-axis is **not** numeric. Accessing this value
      * for e.g., a bar chart, where the x-axis values are strings will trigger
      * a runtime error. You probably want [uniqueXValues] instead.
      */
    lazy val maxX =
      series.map { _.maxX }.max

    /** Lower bound for the y-axis
      *
      * Note: This value assumes that the y-axis is numeric; It is lazy to avoid
      * computing it when the y-axis is **not** numeric. At time of writing, no
      * charts used non-numeric y-values, so you're probably safe calling it.
      */
    lazy val minY =
      series.map { _.minY }.min

    /** Upper bound for the y-axis
      *
      * Note: This value assumes that the y-axis is numeric; It is lazy to avoid
      * computing it when the y-axis is **not** numeric. At time of writing, no
      * charts used non-numeric y-values, so you're probably safe calling it.
      */
    lazy val maxY =
      series.map { _.maxY }.max

    /** Compute all distinct x-axis <b>values</b> from all series
      */
    def uniqueXValues: Set[JsValue] =
      series.flatMap { s =>
        val xType = s.dataframe.schema(s.x).dataType
        s.distinctX.map { SparkPrimitive.encode(_, xType) }
      }.toSet

    def uniqueYValues: Set[JsValue] =
      series.flatMap { s =>
        val yType =
          s.dataframe
            .schema(s.y.head)
            .dataType // Assuming all y columns have the same type
        s.y.flatMap { yVal =>
          s.dataframe
            .select(yVal)
            .distinct
            .collect
            .map(row => SparkPrimitive.encode(row(0), yType))
        }
      }.toSet

    //
    // Offset Heuristics
    //
    // Most of the time, it's a horrible idea to exclude the 0 point on an axis (x- or y-).
    // This makes it very easy to confuse chart viewers' perceptions of relative scales: you
    // can make even a small difference look arbitrarily large.  However, if the values of
    // interest are sufficiently far down on the number line, you won't get to see anything
    // if you don't truncate the axis.
    //
    // A common example is Years.  2000-2023 shows up as barely a blip on [0,2023].
    //
    // For Vizier, we adopt a simple heuristic (illustrated here for the X axis:
    //
    // - if [minX,maxX] spans 0, then obviously we don't need to truncate.
    // - if the difference between maxX and minX is more than 15x the difference between
    //   0 and the closer of minX and maxX, we truncate.
    // - Otherwise we don't truncate.
    //
    // That is, considering one of the following number lines:
    //                                       0 <--- A ---> minX <---- B ----> maxX
    //   minX <--- B ---> maxX <---- A ----> 0
    //
    // We truncate if (15 x B > A)
    //

    /** True if we've heuristically determined that the x-axis should start at a
      * non-zero value
      */
    lazy val xDomainRequiresOffset: Boolean =
      if (minX > 0) {
        (maxX - minX) < minX / 15
      } else {
        if (maxX > 0) { true }
        else {
          (maxX - minX) < (-maxX / 15)
        }
      }

    /** True if we've heuristically determined that the y-axis should start at a
      * non-zero value
      */
    lazy val yDomainRequiresOffset =
      if (minY > 0) {
        (maxY - minY) < minY / 15
      } else {
        if (maxY > 0) { true }
        else {
          (maxY - minY) < (-maxY / 15)
        }
      }

    /** Return the numeric value to be used as the lower bound of the x-axis
      */
    def domainMinX: Double =
      if (xDomainRequiresOffset && minX > 0) { minX }
      else { 0 }

    /** Return the numeric value to be used as the upper bound of the x-axis
      */
    def domainMaxX: Double =
      if (xDomainRequiresOffset || maxX > 0) { maxX }
      else { 0 }

    /** Return the numeric value to be used as the lower bound of the y-axis
      */
    def domainMinY: Double = {
      val calculatedMinY = series.flatMap { s =>
        s.y.map(yVal => s.rows.map(_.getAs[Double](yVal)).min)
      }.min

      if (yDomainRequiresOffset && calculatedMinY > 0) calculatedMinY else 0
    }

    /** Return the numeric value to be used as the upper bound of the y-axis
      */
    def domainMaxY: Double = {
      val calculatedMaxY = series.flatMap { s =>
        s.y.map(yVal => s.rows.map(_.getAs[Double](yVal)).max)
      }.max

      if (yDomainRequiresOffset || calculatedMaxY > 0) calculatedMaxY else 0
    }

    /** Retrieve all [VegaData] objects for this series
      *
      * For any
      */
    def vegaData =
      series.map { _.vegaData } ++
        // Note: vegaRegression returns None if no regression is configured
        // The following list will be empty for series without regressions
        series.flatMap { _.vegaRegression.getOrElse(Seq.empty) }

    def names =
      series.map { _.name }

    ////////////////////// Data (marks)

    def groupMarks(
        markType: VegaMarkType,
        tooltip: Boolean = false,
        fill: Boolean = false,
        opacity: Double = 1.0
    ) =
      series.map { s =>
        val name = s.name
        VegaMark(
          VegaMarkType.Group,
          marks = Some(simpleMarks(markType, tooltip, fill, opacity)),
          encode = Some(
            VegaMarkEncodingGroup(
              // 'enter' defines data in the initial state.
              enter = Some(
                VegaMarkEncoding(
                  x = Some(VegaValueReference.Field(s.x).scale("x"))
                )
              )
            )
          )
        )
      }

    def simpleMarks(
        markType: VegaMarkType,
        tooltip: Boolean = false,
        fill: Boolean = false,
        opacity: Double = 1.0
    ): Seq[VegaMark] =
      series.flatMap { s =>
        val yValsCount =
          if (s.y.length == 1) 2
          else s.y.length;
        s.y.zipWithIndex.flatMap { case (yVal, idx) =>
          val offsetx =
            if (yValsCount > 1) (100 / (yValsCount - 1)) * idx
            else 0.0;
          val encoding = VegaMarkEncoding(
            x =
              if (markType == VegaMarkType.Rect)
                Some(VegaValueReference.Field(s.x).scale("x").offset(offsetx))
              else Some(VegaValueReference.Field(s.x).scale("x")),
            y = Some(VegaValueReference.Field(yVal).scale("y")),
            stroke =
              if (markType == VegaMarkType.Rect)
                Some(
                  VegaValueReference.Literal(JsString(s.name)).scale("color")
                )
              else
                Some(VegaValueReference.Literal(JsString(yVal)).scale("color")),
            fill =
              if (!fill) None
              else
                Some(VegaValueReference.Literal(JsString(s.color))),
            tooltip =
              if (!tooltip) None
              else Some(VegaValueReference.Signal("datum")),
            opacity =
              if (opacity >= 1.0) None
              else Some(opacity),
            width =
              if (markType == VegaMarkType.Rect)
                Some(VegaValueReference.Band(1.0 / yValsCount).scale("xInner"))
              else None,
            y2 =
              if (markType == VegaMarkType.Rect)
                Some(
                  VegaValueReference.ScaleTransform(
                    "y",
                    VegaValueReference.Literal(JsNumber(0))
                  )
                )
              else None
          )
          Some(
            VegaMark(
              markType,
              from = Some(VegaFrom(data = s.name)),
              encode = Some(
                VegaMarkEncodingGroup(
                  enter = Some(encoding)
                )
              )
            )
          )
        }
      } ++ series.flatMap { s =>
        s.regression
          .map { _ =>
            s.y.map { yVal =>
              Some(
                VegaMark(
                  VegaMarkType.Line,
                  from = Some(VegaFrom(data = s.regressionName)),
                  encode = Some(
                    VegaMarkEncodingGroup(
                      enter = Some(
                        VegaMarkEncoding(
                          x = Some(VegaValueReference.Field(s.x).scale("x")),
                          y = Some(VegaValueReference.Field(yVal).scale("y")),
                          stroke = Some(
                            VegaValueReference
                              .Literal(JsString(s.name))
                              .scale("color")
                          )
                        )
                      )
                    )
                  )
                )
              )
            }
          }
          .getOrElse(Seq.empty)
          .flatten
      }
  }
}
