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
package info.vizierdb.commands.data

import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import info.vizierdb.types.ArtifactType
import info.vizierdb.VizierException
import info.vizierdb.spark.InlineDataConstructor
import org.apache.spark.sql.types.{ StructField, StringType }
import info.vizierdb.viztrails.ProvenancePrediction

object EmptyDataset extends Command
{
  def name: String = "Create Empty Dataset"
  def parameters: Seq[Parameter] = Seq(
    StringParameter(id = "name", name = "Name of Dataset")
  )
  def format(arguments: Arguments): String = 
    s"CREATE EMPTY DATASET ${arguments.pretty("name")}"
  def title(arguments: Arguments): String = 
    s"Initialize ${arguments.pretty("name")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val schema = Seq(StructField("unnamed_column", StringType))
    context.outputDataset(
      arguments.get[String]("name"),
      InlineDataConstructor(
        schema = schema,
        data = Seq(Seq(JsString("")))
      )
    )
    context.message("Empty Dataset Created")
  }
  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyWrites(arguments.get[String]("name"))
      .andNothingElse
}

