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

object MergeColumns
  extends LensCommand
{ 
  def lens = Lenses.picker
  def name: String = "Merge Columns"
  def lensParameters: Seq[Parameter] = Seq(
    ListParameter(id = "schema", name = "Columns", components = Seq(
      ColIdParameter(id = "pickFrom", name = "Input")
    )),
    StringParameter(id = "pickAs", name = "Output")
  )
  def lensFormat(arguments: Arguments): String = 
    s"MERGE COLUMNS ${arguments.getList("schema").map { _.get[Int]("pickFrom").toString }.mkString(", ")} INTO ${arguments.get[String]("pickAs")}"

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    Json.toJson(
      MergeAttributesLensConfig(
        inputs = arguments.getList("schema")
                          .map { _.get[Int]("pickFrom") }
                          .map { schema(_).name },
        output = arguments.get[String]("pickAs"),
        dataType = None
      )
    )
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = Map.empty
}

