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
import info.vizierdb.types._
import info.vizierdb.VizierException
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.MaterializeConstructor
import info.vizierdb.Vizier
import info.vizierdb.filestore.Staging
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.catalog.CatalogDB

object CheckpointDataset extends Command
{
  def name: String = "Checkpoint Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
  )
  def format(arguments: Arguments): String = 
    s"CHECKPOINT ${arguments.get[String]("dataset")}"
  def title(arguments: Arguments): String = 
    format(arguments)
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val input = context.artifact(datasetName)
                          .getOrElse{ 
                            context.error(s"Dataset $datasetName does not exist"); return
                          }

    val df = CatalogDB.withDB { implicit s => input.dataframe }

    context.message("Checkpointing data...")

    val artifact = context.outputDatasetWithFile(name, { artifact => 
      Staging.stage( 
        input = df, 
        format = MaterializeConstructor.DEFAULT_FORMAT,
        projectId = context.projectId,
        artifactId = artifact.id,
      ) 
      context.message("Dataset written, registering file...")
      new MaterializeConstructor(
        input = input.id,
        schema = df.schema,
        artifactId = artifact.id,
        projectId = context.projectId,
        format = MaterializeConstructor.DEFAULT_FORMAT,
        options = Map.empty
      )
    })

    context.message(s"Dataset Checkpointed as ${artifact.relativeFile}")
  }
  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String]("dataset"))
      .definitelyWrites(arguments.get[String]("dataset"))
      .andNothingElse
}

