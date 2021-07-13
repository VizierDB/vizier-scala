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
package info.vizierdb.commands.transform

import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.types.ArtifactType
import info.vizierdb.catalog.Artifact

object FilterDataset 
  extends SQLTemplateCommand
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_FILTER = "filter"

  def name = "Filter Dataset"
  def templateParameters = Seq[Parameter](
    DatasetParameter(id = PARAM_DATASET, name = "Input Dataset"),
    StringParameter(id = PARAM_FILTER, name = "Filter")
  )

  def format(arguments: Arguments): String = 
    s"SELECT * FROM ${arguments.get[String](PARAM_DATASET)} WHERE ${arguments.get[String](PARAM_FILTER)}"

  def title(arguments: Arguments): String =
  {
    s"Filter ${arguments.get[String](PARAM_DATASET)}"
  }

  def query(arguments: Arguments, context: ExecutionContext): (Map[String, Artifact], String) =
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val dataset = context.artifact(datasetName) 
                         .getOrElse { throw new RuntimeException(s"Dataset $datasetName not found.")}
    if(dataset.t != ArtifactType.DATASET){
      throw new RuntimeException(s"$datasetName is not a dataset")
    }
    val query = s"SELECT * FROM __input__dataset__ WHERE ${arguments.get[String](PARAM_FILTER)}"
    val deps = Map("__input__dataset__" -> dataset)
    return (deps, query)
  }

  def predictProvenance(arguments: Arguments): Option[(Seq[String], Seq[String])] = 
    Some( (
      Seq(arguments.get[String](PARAM_DATASET)),
      Seq(arguments.getOpt[String](PARAM_OUTPUT_DATASET).getOrElse(DEFAULT_DS_NAME))
    ) )
}
