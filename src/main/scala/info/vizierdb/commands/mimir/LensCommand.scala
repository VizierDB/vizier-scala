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
import org.mimirdb.lenses.Lens
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.api.request.CreateLensRequest
import org.apache.spark.sql.types.StructField
import info.vizierdb.viztrails.ProvenancePrediction

trait LensCommand 
  extends Command
  with LazyLogging
{
  def lensParameters: Seq[Parameter]
  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String, JsValue]
  def lens: String
  def lensFormat(arguments: Arguments): String

  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset")
  ) ++ lensParameters

  def format(arguments: Arguments): String =
    s"CREATE LENS ON ${arguments.get[String]("dataset")} ${lensFormat(arguments)}"
  def title(arguments: Arguments): String =
    s"$lens on ${arguments.pretty("dataset")}"

  def process(arguments: Arguments, context: ExecutionContext)
  {
    val datasetName = arguments.get[String]("dataset")

    logger.debug(s"${lens}($arguments) <- $datasetName")
    

    val input = context.dataset(datasetName)
                         .getOrElse { 
                            throw new IllegalArgumentException(s"No such dataset '$datasetName'")
                         }

    val schema = context.datasetSchema(datasetName).get
    val config = lensConfig(arguments, schema, datasetName, context)

    val (output, _) = context.outputDataset(datasetName)

    logger.debug(s"${this.getClass().getName()} -> $input -> $output")

    val response = CreateLensRequest(
      input = input,
      `type` = lens,
      params = config,
      resultName = Some(output),
      materialize = false,
      humanReadableName = Some(datasetName),
      properties = None
    ).handle

    val updates = updateConfig(response.config, schema, datasetName)
    if( !updates.isEmpty ){
      context.updateJsonArguments(updates.toSeq:_*)
    }

    context.message(s"Created $name Lens on $datasetName")
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String]("dataset"))
      .definitelyWrites(arguments.get[String]("dataset"))
      .andNothingElse

}

