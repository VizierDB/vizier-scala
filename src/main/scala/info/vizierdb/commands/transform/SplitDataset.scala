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
import info.vizierdb.viztrails.ProvenancePrediction
import org.mimirdb.api.request.CreateViewRequest
import info.vizierdb.VizierException

object SplitDataset 
  extends Command
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_CONDITION = "condition"
  val PARAM_IF_TRUE = "if_true"
  val PARAM_IF_FALSE = "if_false"

  val name = "Split Dataset"

  val parameters = Seq[Parameter](
    DatasetParameter(PARAM_DATASET, "Dataset"),
    StringParameter(PARAM_CONDITION, "Condition"),
    StringParameter(PARAM_IF_TRUE, "Pass Dataset"),
    StringParameter(PARAM_IF_FALSE, "Fail Dataset", required = false)
  )

  def format(arguments: Arguments) =
    s"""SPLIT ${arguments.pretty(PARAM_DATASET)} ON ${arguments.pretty(PARAM_CONDITION)} 
       |PASSING RECORDS INTO ${arguments.pretty(PARAM_IF_TRUE)}""".stripMargin + 
       arguments.getOpt[String](PARAM_IF_FALSE)
                .map { "\nFAILING RECORDS INTO "+_ }
                .getOrElse("")

  def title(arguments: Arguments) = 
    s"Split ${arguments.pretty(PARAM_DATASET)} INTO ${arguments.pretty(PARAM_IF_TRUE)}"+
       arguments.getOpt[String](PARAM_IF_FALSE)
                .map { " and "+_ }
                .getOrElse("")

  def process(arguments: Arguments, context: ExecutionContext)
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val inputDataset = context.artifact(datasetName)
                              .getOrElse {
                                throw new VizierException("Expected dataset parameter")
                              }
    val condition = arguments.get[String](PARAM_CONDITION)
    val outputs = Seq(
      (arguments.get[String](PARAM_IF_TRUE), condition)
    ) ++ arguments.getOpt[String](PARAM_IF_FALSE).map { 
      (_, s"NOT ($condition)")
    }

    try { 
      for( (outputTable, outputCondition) <- outputs){
        logger.trace("Creating view for $outputTable / $outputCondition")
        val (outputName, _) = context.outputDataset(outputTable)
        val response = CreateViewRequest(
          input = Map(
            "INPUT" -> inputDataset.nameInBackend
          ),
          functions = None,
          query = s"SELECT * FROM INPUT WHERE ${outputCondition}", 
          resultName = Some(outputName),
          properties = None
        ).handle

        logger.trace("Rendering dataset summary")
        context.displayDataset(outputTable)
      }
    } catch { 
      case e: org.mimirdb.api.FormattedError => 
        context.error(e.response.errorMessage)
    }
  }

  def predictProvenance(arguments: Arguments): ProvenancePrediction = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](PARAM_DATASET))
      .definitelyWrites((
        Seq(arguments.get[String](PARAM_IF_TRUE)) ++ 
          arguments.getOpt[String](PARAM_IF_FALSE)):_*)
      .andNothingElse
}