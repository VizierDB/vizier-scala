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
import org.mimirdb.lenses.implementation.MergeAttributesLensConfig
import org.mimirdb.lenses.Lenses
import org.mimirdb.spark.Schema
import org.mimirdb.spark.SparkPrimitive.dataTypeFormat

object TypeInference
  extends LensCommand
{ 
  def lens = Lenses.typeInference
  def name: String = "Assign Datatypes"
  def lensParameters: Seq[Parameter] = Seq(
    // TemplateParameters.SCHEMA
  )
  def lensFormat(arguments: Arguments): String = 
    "CAST TYPES"
    // s"CAST TYPES TO (${arguments.getList("schema").map { col => 
    //   s"${col.get[String]("schema_column")} ${col.get[String]("schema_datatype")}"
    // }.mkString(", ")})"


  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
    JsObject(Map[String,JsValue]())
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] =
    Map.empty
}

