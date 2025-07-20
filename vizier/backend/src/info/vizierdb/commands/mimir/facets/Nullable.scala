/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.commands.mimir.facets

import play.api.libs.json._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.{ count, sum, when, lit }
import com.typesafe.scalalogging.LazyLogging

case class Nullable(column: String, percent: Option[Double])
  extends Facet
  with LazyLogging
{
  def identity = Nullable.identity
  def description = 
    percent.map { pctExpected =>
      s"$column has no more than ${(pctExpected * 1000).toInt / 10.0}% nulls"
    }.getOrElse { 
      s"$column has no nulls"
    }
  def test(query: DataFrame): Seq[String] =
  {
    if(!(query.columns contains column)){ return Seq() }

    val (numRows, numNulls) = 
      query
        .agg(sum(lit(1l)), sum(when( query(column).isNull, 1l ).otherwise(0l)))
        .take(1)
        .map { row => (row.getAs[Long](0), row.getAs[Long](1)) }
        .head

    logger.debug(s"Trace found: $numRows / $numNulls for $column")
    percent match {
      case None => {
        if(numNulls > 0){
          Seq(s"$column had no nulls before, but now has $numNulls")
        } else {
          Seq[String]()
        }
      }
      case Some(pctExpected) => {
        val pctActual = numNulls.toDouble / numRows
        if(pctActual > pctExpected * 1.1){ // add a small buffer
          Seq(s"$column had only ${(pctExpected * 1000).toInt / 10.0}% nulls before, but now has ${(pctActual * 1000).toInt / 10.0}%")
        } else {
          Seq[String]()
        }
      }

    }
  }
  def toJson = Json.toJson(this)
  def affectsColumn = Some(column)
}


object Nullable
  extends FacetDetector
  with LazyLogging
{
  def identity = "Nullable"
  implicit val format: Format[Nullable] = Json.format

  def apply(query: DataFrame): Seq[Facet] =
  {
    val (numRows, numNullsByColumn) = 
      query
        .agg(sum(lit(1l)).as("__COUNT"), 
          query.columns.map { column => 
            sum(when( query(column).isNull, 1l ).otherwise(0l))
              .as(column)
          }:_*
        )
        .take(1)
        .map { row => 
          ( 
            row.getAs[Long](0), 
            query.columns.zipWithIndex.map { case (column, idx) =>
              column -> row.getAs[Long](idx+1)
            }
          )
        }
        .head
    
    logger.debug(s"Test found: $numRows / $numNullsByColumn")
    
    numNullsByColumn.flatMap { case (column, numNulls) =>
      val pctNulls = numNulls.toDouble / numRows
  
      if(numNulls == 0) {
        logger.trace(s"No nulls in $column")
        Some(Nullable(column, None))
      } else if(pctNulls < 0.8){
        logger.trace(s"${(pctNulls*100).toInt}% nulls in $column")
        Some(Nullable(column, Some(pctNulls)))
      } else {
        logger.trace(s"Too many (${(pctNulls*100).toInt}%) nulls in $column")
        None
      }
    }
  }
  def decode(facet: JsValue)(): Facet = facet.as[Nullable]
}
