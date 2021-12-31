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

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.{DataFrameConstructor, DataFrameConstructorCodec, DefaultProvenance}

case class LensConstructor(
  lensClassName: String,
  target: Identifier,
  arguments: JsObject,
  projectId: Identifier
) extends DataFrameConstructor
  with DefaultProvenance
{
  def construct(context: Identifier => DataFrame): DataFrame =
  {
    val lensClazz = 
      Class.forName(lensClassName)
    val lens = 
      lensClazz
           .getField("MODULE$")
           .get(lensClazz)
           .asInstanceOf[LensCommand]

    return lens.build(
      context(target),
      Arguments(arguments, lens.parameters),
      projectId
    )
  }

  def dependencies = Set(target)
}

object LensConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[LensConstructor] = Json.format
  def apply(j: JsValue) = j.as[LensConstructor]
}


trait LensCommand 
  extends Command
  with LazyLogging
{
  val PARAM_DATASET = "dataset"

  def lensParameters: Seq[Parameter]
  def train(dataset: DataFrame, arguments: Arguments, context: ExecutionContext): Map[String, Any]
  def build(dataset: DataFrame, arguments: Arguments, projectId: Identifier): DataFrame

  def parameters = Seq(
    ArtifactParameter(id = PARAM_DATASET, name = "Dataset", artifactType = ArtifactType.DATASET),
  ) ++ lensParameters
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val dataset = context.artifact(datasetName)
                         .getOrElse{ 
                           context.error(s"Dataset $datasetName does not exist"); return
                         }

    logger.debug(s"${name}($arguments) <- $datasetName")
    context.message(s"Building $name lens on $datasetName...")

    context.message(s"Saving results...")
    
    val updatesFromTraining = 
      train(
        DB.autoCommit { implicit s => dataset.dataframe },
        arguments,
        context
      )
    val updatedArguments = 
      if(updatesFromTraining.isEmpty){ arguments }
      else { context.updateArguments(updatesFromTraining.toSeq:_*) }

    val output = context.outputDataset(
      datasetName,
      new LensConstructor(
        lensClassName = this.getClass.getName, 
        target = dataset.id,
        arguments = updatedArguments.asJson,
        projectId = context.projectId,
      )
    )
    context.displayDataset(datasetName)
  }

  def predictProvenance(arguments: Arguments): Option[(Seq[String], Seq[String])] = 
    Some(
      Seq(arguments.get[String](PARAM_DATASET)),
      Seq(arguments.get[String](PARAM_DATASET))
    )
}
trait UntrainedLens
{
  def train(dataset: DataFrame, arguments: Arguments, context: ExecutionContext) = Map[String,Any]()
}

