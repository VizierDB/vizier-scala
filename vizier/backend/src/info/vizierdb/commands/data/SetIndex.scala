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
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import info.vizierdb.types.ArtifactType
import info.vizierdb.VizierException
import info.vizierdb.viztrails.ProvenancePrediction

object SetIndex extends Command
{
  /** WORK IN PROGRESS: NOT INSTALLED YET **/

  def name: String = "Set Index Column"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    ColIdParameter(id = "column", name = "Column")
  )
  def format(arguments: Arguments): String = 
    s"SET INDEX FOR ${arguments.get[String]("dataset")} TO COLUMN ${arguments.get[Int]("column")}"
  def title(arguments: Arguments): String = 
    s"${arguments.get[String]("dataset")}.INDEX = ${arguments.get[Int]("column")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val artifact = context.artifact(datasetName)
                          .getOrElse{ 
                            context.error(s"Dataset $datasetName does not exist"); return
                          }

    ???


  }
  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String]("dataset"))
      .definitelyWrites(arguments.get[String]("dataset"))
      .andNothingElse
}

