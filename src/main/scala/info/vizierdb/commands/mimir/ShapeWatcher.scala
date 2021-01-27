/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.ShapeWatcherConfig
import org.mimirdb.lenses.Lenses

object ShapeWatcher
  extends LensCommand
{ 
  def lens = Lenses.shapeWatcher
  def name: String = "Dataset Stabilizer"
  def lensParameters: Seq[Parameter] = Seq(
    StringParameter(id = "config", name = "Output", required = false, hidden = true, default = Some("")),
    BooleanParameter(id = "recompute", name = "Rediscover Parameters", required = true, default = Some(true))
  )

  def config(arguments: Arguments):JsValue = 
      arguments.getOpt[String]("config")
               .map { Json.parse(_) }
               .getOrElse { JsNull }
  def lensFormat(arguments: Arguments): String = 
    s"WATCH FOR CHANGES"+(
      config(arguments) match {
        case JsNull => ""
        case x => 
          " EXPECTING ("+
            x.as[ShapeWatcherConfig]
             .facets
             .map { "\n  "+_.description }
             .mkString(",")+"\n)"
      }
    )

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
    if(arguments.get[Boolean]("recompute")){ JsNull }
    else { config(arguments) }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = 
    Map( "config" -> JsString(lensArgs.toString()), "recompute" -> JsBoolean(false) )
}

