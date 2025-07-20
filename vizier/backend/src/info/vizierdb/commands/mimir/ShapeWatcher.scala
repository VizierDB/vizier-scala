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
package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import info.vizierdb.commands.mimir.facets._
import org.apache.spark.sql.DataFrame
import info.vizierdb.types._
import org.mimirdb.caveats.implicits._

object ShapeWatcher
  extends LensCommand
{ 
  val PARAM_FACETS = "facets"
  val PARAM_CONFIG = "config"

  def name: String = "Dataset Stabilizer"

  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = PARAM_FACETS, name = "Facets (leave blank to guess)", required = false, components = Seq(
      StringParameter(id = PARAM_CONFIG, name = "Configuration", hidden = false),
    ))
  )

  def config(arguments: Arguments):Seq[Facet] = 
      arguments.getList(PARAM_FACETS)
               .map { f => Json.parse(f.getOpt[String](PARAM_CONFIG)
                                       .getOrElse { "[]" })
                                       .as[Facet] }
  def format(arguments: Arguments): String = 
    s"WATCH FOR CHANGES"+(
      config(arguments) match {
        case Seq() => ""
        case x => 
          " EXPECTING ("+
            x.map { "\n  "+_.description }
             .mkString(",")+"\n)"
      }
    )

  def title(arguments: Arguments): String =
    s"WATCH ${arguments.pretty(PARAM_DATASET)}"

  def train(df: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any] =
  {
    if(!arguments.getList(PARAM_FACETS).isEmpty){ return Map.empty }

    val detectedFacets = Facet.detect(df)

    Map(
      PARAM_FACETS ->
        detectedFacets
             .map { f => Map(
                PARAM_CONFIG -> Json.toJson(f) 
              )}
    )

  }

  def build(input: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame =
  {
    config(arguments).flatMap { facet => 
      facet.test(input).map { _ -> facet.affectsColumn }
    }.foldLeft(input){ (df:DataFrame, error:(String, Option[String])) => 
      logger.debug(s"Building $df <- $error")
      error match {
        case (msg, None) => df.caveat(msg)
        case (msg, Some(errorColumn)) => 
          df.select(
            df.columns.map { col =>
              if(col.equalsIgnoreCase(errorColumn)){ 
                df(col).caveat(msg).as(col)
              } else { 
                df(col)
              }
            }:_*
          )
      }
    }

  }

}

