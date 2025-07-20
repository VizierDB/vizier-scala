/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import info.vizierdb.types.ArtifactType
import info.vizierdb.VizierException
import info.vizierdb.viztrails.ProvenancePrediction

object CloneDataset extends Command
{
  def name: String = "Clone Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    StringParameter(id = "name", name = "Name of Copy")
  )
  def format(arguments: Arguments): String = 
    s"CLONE ${arguments.get[String]("dataset")} TO ${arguments.pretty("name")}"
  def title(arguments: Arguments): String = format(arguments)
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val artifact = context.artifact(datasetName)
                         .getOrElse{ 
                           context.error(s"Dataset $datasetName does not exist"); return
                         }

    context.output(arguments.get[String]("name"), artifact)
    context.message("Dataset Cloned")
  }
  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String]("dataset"))
      .definitelyWrites(arguments.get[String]("name"))
      .andNothingElse
}

