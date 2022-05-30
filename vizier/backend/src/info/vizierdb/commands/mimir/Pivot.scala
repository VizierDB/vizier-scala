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
package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.VizierException
import info.vizierdb.types._
import org.apache.spark.sql.types.{ StructField, StringType }
import org.apache.spark.sql.{ DataFrame, Column }
import org.apache.spark.sql.functions._
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.caveats.implicits._

/**
 * The pivot lens "pivots" a table, modifying rows into columns.
 *
 * For example
 * 
 *  Year  |  Color  | Price
 * -------+---------+--------
 *  2020  |  Blue   |  100k
 *  2019  |  Blue   |  90k
 *  2020  |  Red    |  120k
 *  2019  |  Red    |  130k
 * 
 * Pivoting this with 'year' as a target, 'price' as a value, and 'color' as a key produces:
 * 
 *  Color | Price_2019 | Price_2020
 * -------+------------+------------
 *  Blue  |   90k      |   100k
 *  Red   |   130k     |   120k
 *
 * In summary, every `value` column is split into N copies, where N is the number of distinct
 * values of the `target` column.  For each distinct value of `key`, the lens will emit one row
 * containing the corresponding value of the `value` column at the intersection of the `target` and
 * and `key` values.  If no such rows exist, the cell will be NULL.  If multiple *distinct* values
 * exist, the cell contents will be arbitrary.
 *
 * In either case, if there is not exactly one value that can be placed into a single cell in the
 * pivot table, the cell will be caveated.
 */
object Pivot
  extends LensCommand
  with LazyLogging
{ 
  val DISTINCT_LIMIT = 50
  val PARAM_TARGET = "target"
  val PARAM_KEYS = "keys"
  val PARAM_COLUMN = "column"
  val PARAM_PIVOTS = "pivots"
  val PARAM_VALUE = "value"

  def name: String = "Pivot Dataset"
  def lensParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = PARAM_TARGET, name = "Pivot Column"),
    ListParameter(id = PARAM_KEYS, name = "Group By", components = Seq(
      ColIdParameter(id = PARAM_COLUMN, name = "Column")
    )),
    ListParameter(id = PARAM_PIVOTS, name = "Pivot Values", components = Seq(
      StringParameter(id = PARAM_VALUE, name = "Pivot Value"),
    ), hidden = true)
  )

  def format(arguments: Arguments): String = 
    s"PIVOT ON ${arguments.get[Int]("target")} GROUP BY ${arguments.getList("keys").map { _.get[Int]("column") }.mkString(", ")}"

  def title(arguments: Arguments): String =
    s"PIVOT ${arguments.pretty(PARAM_DATASET)}"

  def train(df: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any] =
  {
    val target = df.columns(arguments.get[Int](PARAM_TARGET))
    val pivots = 
      df.select( df(target).cast(StringType) )
        .distinct()
        .take(DISTINCT_LIMIT + 1)
        .map { _.getString(0) }
        .toSet

    if(pivots.size > DISTINCT_LIMIT){
      throw new VizierException(s"Can't pivot on a column with more than $DISTINCT_LIMIT values")
    }
    if(pivots.size == 0){
      throw new VizierException(s"Can't pivot on an empty column")
    }

    Map(
      PARAM_PIVOTS -> pivots.map { v => Map(PARAM_VALUE -> v) }
    )
  }
  def build(df: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame = 
  {
    val schema = df.schema
    val pivotColumn = schema(arguments.get[Int](PARAM_TARGET)).name
    val pivots = arguments.getList(PARAM_PIVOTS)
                          .map { _.get[String](PARAM_VALUE) }
    val gbKeys = arguments.getList(PARAM_KEYS)
                          .map { a => schema(a.get[Int](PARAM_COLUMN)).name }
    val isSpecialColumn = (gbKeys :+ pivotColumn).toSet
    val valueColumns = df.columns.filterNot { isSpecialColumn(_) }

    val safePivots = 
      pivots.map { p => p -> p.replaceAll("[^0-9a-zA-Z_]+", "_") }

    def selectedValueColumn(v: String, p: String) = s"first_${v}_${p}"
    def countColumn(v: String, p: String)         = s"count_${v}_${p}"

    def keyDescriptionParts = 
      if(gbKeys.isEmpty) { Seq() }
      else if(gbKeys.size == 1) { Seq(lit(" on row "), col(gbKeys.head).cast(StringType))}
      else {
        // Emits ' x < ${key1}, ${key2}, ${key3}, ... >'
        lit(" on row < ") +: 
          gbKeys
                .flatMap { k => Seq(lit(", "), lit(s"$k : "), col(k).cast(StringType)) }
                .drop(1) :+
          lit(" >")
      }

    val (pivotColumnValues, pivotColumnCounts, caveatedPivotColumns) = 
      valueColumns
        .flatMap { valueName =>  
          val value = df(valueName)
          safePivots.map { case (pivot, safePivot) =>
            val valueIfPivotOtherwiseNull =
              when(df(pivotColumn).cast(StringType) === pivot, value)
                    .otherwise(lit(null))

            val caveatMessage = concat((Seq[Column](
                when(col(countColumn(valueName, safePivot)) === 0, "No")
                  .otherwise(col(countColumn(valueName, safePivot)).cast(StringType)),
                lit(s" possible values for ${valueName}_${pivot}")
              ) ++ keyDescriptionParts 
                :+ lit(s" ${arguments.pretty(PARAM_DATASET)} (pivoted on ${pivotColumn})")
            ):_*) 

            val caveatCondition = 
              col(countColumn(valueName, safePivot)) =!= 1

            (
              first(valueIfPivotOtherwiseNull, ignoreNulls = true)
                .as(selectedValueColumn(valueName, safePivot)), 
              countDistinct(valueIfPivotOtherwiseNull)
                .as(countColumn(valueName, safePivot)), 
              col(selectedValueColumn(valueName, safePivot))
                .caveatIf(caveatMessage, caveatCondition)
                .as(s"${valueName}_${pivot}")
            )
          }:Seq[(Column, Column, Column)]
        }
        .unzip3 

    val intermediateColumns = (pivotColumnValues ++ pivotColumnCounts)
    
    logger.debug(s"INTERMEDIATE: ${intermediateColumns.map { "\n    "+_.toString }.mkString}")

    val pivotedInputWithCounts =
      if(gbKeys.isEmpty){
        df.agg( intermediateColumns.head, intermediateColumns.tail:_* )
      } else {
        df.groupBy( gbKeys.map { df(_) }:_*)
             .agg( intermediateColumns.head, intermediateColumns.tail:_* )
      }


    val outputColumns =
      gbKeys.map { pivotedInputWithCounts(_) } ++ 
      caveatedPivotColumns

    logger.debug(s"OUTPUT: ${outputColumns.map { "\n    "+_.toString }.mkString}")

    return pivotedInputWithCounts.select(outputColumns:_*)
  }

}

