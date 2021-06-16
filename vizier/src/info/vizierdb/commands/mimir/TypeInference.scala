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
    ListParameter(name = "Schema (leave blank to guess)", id = "schema", required = false, components = Seq(
      ColIdParameter(name = "Column Name", id = "schema_column", required = false),
      TemplateParameters.DATATYPE
    )),
  )
  def lensFormat(arguments: Arguments): String = 
    // "CAST TYPES"
    s"CAST TYPES TO (${arguments.getList("schema").map { col => 
      s"${col.get[Int]("schema_column")} ${col.get[String]("schema_datatype")}"
    }.mkString(", ")})"


  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    JsObject(
      arguments.getList("schema")
               .filter { _.get[Int]("schema_column") >= 0 }
               .map { col =>
                 schema(col.get[Int]("schema_column")).name -> 
                   JsString(col.get[String]("schema_datatype"))
               }
               .toMap
    )
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] =
  {
    val trained = lensArgs.as[Map[String, String]]
                          .mapValues { 
                            case "integer" => "int"
                            case x => x
                          }
    Map(
      "schema" -> 
        JsArray(
          schema.zipWithIndex.map { case (col, idx) =>
            Json.obj(
              "schema_column" -> idx,
              "schema_datatype" -> trained(col.name)
            )
          }
        )
    )
  }  
}

