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
package info.vizierdb.artifacts

import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, Row }
import org.apache.spark.unsafe.types.UTF8String
import org.mimirdb.caveats.implicits._
import info.vizierdb.api.response.ErrorResponse
import info.vizierdb.spark.caveats.QueryWithCaveats


case class ChartSeries(
  column: String, 
  label: Option[String], 
  constraint: Option[String]
){
  def schema: JsValue =
    Json.obj(
      "column" -> column,
      "label" -> JsString(label.getOrElse { column })
    )
}

case class Chart(
  dataset: String,
  name: String,
  chartType: String,
  grouped: Boolean,
  xaxis: Option[String],
  xaxisConstraint: Option[String],
  series: Seq[ChartSeries]
){
  def allColumns = 
    (xaxis.map { ChartSeries(_, None, xaxisConstraint) } ++: series)

  def schema: JsValue =
    Json.obj(
      "id"           -> JsNull,
      "dataset"      -> dataset,
      "name"         -> name,
      "data"         -> allColumns.map { _.schema },
      "chartType"    -> chartType,
      "groupedChart" -> grouped,
      "xAxis"        -> xaxis.map { _ => 0 }
    )

  def render(df: DataFrame): JsValue =
  {
    var query = df
    // if(xaxis.isDefined){
    //   query = query.filter( df(xaxis.get).isNotNull )
    // }
    query = query
              .select(
                allColumns.map { case ChartSeries(col, _, constraint) =>
                  // TODO: Don't ignore "constraint"
                  df(col)
                }:_*
              )

    val result = 
      query.trackCaveats
            .stripCaveats
            .take(QueryWithCaveats.RESULT_THRESHOLD+1)

    if(result.size >= QueryWithCaveats.RESULT_THRESHOLD){
      throw new QueryWithCaveats.ResultTooBig
    }


    def getColumn(column: String): Seq[JsValue] = 
    {
      if(result.isEmpty) { Seq() } 
      else {
        val fieldIndex: Int = result.head.fieldIndex(column)
        result.map { row =>
          if(row.isNullAt(fieldIndex)){
            JsNull
          } else {
            row.getAs[Any](fieldIndex) match {
              case x:Short => JsNumber(x.toLong)
              case x:Integer => JsNumber(x.toLong)
              case x:Long => JsNumber(x)
              case x:Float => JsNumber(x)
              case x:Double => JsNumber(x)
              case x:String => JsString(x)
              case x:UTF8String => JsString(x.toString)
              case x => throw new IllegalArgumentException(s"Unsupported value ($x:${x.getClass.getSimpleName}) in column $column")
            }
          }
        }
      }
    }

    val seriesData = 
      series.map { case ChartSeries(column, label, _) =>
          Json.obj(
            "label" -> (label.map { _.trim } match {
                          case None => JsString(column)
                          case Some("") => JsString(column)
                          case Some(l) => JsString(l)
                        }),
            "data" -> getColumn(column),
            "caveats" -> result.map { _.isCaveatted(column) }
          )
        }

    return Json.obj(
      "data" -> schema,
      "result" -> JsObject(Map[String,JsValue](
        "chart" -> Json.obj(
          "type" -> chartType,
          "grouped" -> grouped
        ),
        "series" -> JsArray(seriesData)
      ) ++ xaxis.map { col => 
        "xAxis" -> Json.obj(
          "data" -> getColumn(col),
          "caveats" -> result.map { _.isCaveatted }
        )
      }.toMap)
    )
  }
}

