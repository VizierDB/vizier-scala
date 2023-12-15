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
package info.vizierdb.spark

import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.commands.FileArgument
import org.apache.spark.sql.DataFrame
import play.api.libs.json.JsValue
import org.apache.spark.ml.PipelineModel
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.catalog.Artifact

case class PipelineModelConstructor(
  input: Identifier,
  url: FileArgument,
  projectId: Identifier,
  schema: Seq[StructField]
) extends DataFrameConstructor 
  with DefaultProvenance
{

  override def dependencies: Set[Identifier] = Set(input)

  def pipeline: PipelineModel = 
    PipelineModel.load(url.getPath(projectId, noRelativePaths = true)._1)

  override def construct(context: Identifier => Artifact): DataFrame = 
    pipeline.transform(context(input).dataframeFromContext(context))


}

object PipelineModelConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[PipelineModelConstructor] = Json.format

  override def apply(j: JsValue): DataFrameConstructor = j.as[PipelineModelConstructor]
}