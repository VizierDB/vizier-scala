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

import scalikejdbc._
import play.api.libs.json.JsValue
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
    val artifact = context.artifact(datasetName)
                          .getOrElse{ 
                            context.error(s"Dataset $datasetName does not exist"); return
                          }

    val df = DB.readOnly { implicit s => artifact.dataframe }

    context.message("Checkpointing data...")

    val staged = context.outputFile(
      "datasetName",
      mimeType = MIME.RAW,
    )
    Staging.stage( 
            input = df, 
            format = MaterializeConstructor.DEFAULT_FORMAT,
            projectId = context.projectId,
            artifactId = staged.id,
          ) 
    context.message("Dataset written, registering file...")

    context.outputDataset(
      datasetName,
      new MaterializeConstructor(
        input = artifact.id,
        schema = df.schema,
        url = if(Staging.stagingDefaultsToRelative) { staged.relativeFile.toString } 
              else { staged.absoluteFile.toString },
        format = MaterializeConstructor.DEFAULT_FORMAT,
        options = Map.empty,
        urlIsRelative = Some(Staging.stagingDefaultsToRelative)
      )
    )
    context.message("Dataset Checkpointed")
  }
  def predictProvenance(arguments: Arguments) = 
    Some( (Seq(arguments.get[String]("dataset")), Seq.empty) )
}

