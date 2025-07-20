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
package info.vizierdb.commands.transform

import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.types.ArtifactType
import info.vizierdb.catalog.Artifact
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.VizierException
import info.vizierdb.util.StringUtils
import play.api.libs.json.JsObject
import info.vizierdb.spark.ViewConstructor
import info.vizierdb.api.FormattedError
import info.vizierdb.types._
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.DataType

object SplitDataset 
  extends Command
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_PARTITIONS = "partition"
  val PARAM_CONDITION = "condition"
  val PARAM_OUTPUT = "output"
  val PARAM_OTHERWISE = "otherwise_output"

  val name = "Split Dataset"

  val parameters = Seq[Parameter](
    DatasetParameter(PARAM_DATASET, "Dataset"),
    ListParameter(PARAM_PARTITIONS, "Partitions", components = Seq(
      StringParameter(PARAM_CONDITION, "Condition"),
      StringParameter(PARAM_OUTPUT, "Output Dataset"),
    )),
    StringParameter(PARAM_OTHERWISE, "Otherwise Dataset", required = false)
  )

  def format(arguments: Arguments) =
  {
    val names:Seq[String] = 
      arguments.getList(PARAM_PARTITIONS)
               .map { a => s"${a.pretty(PARAM_OUTPUT)} (${a.pretty(PARAM_CONDITION)})" } ++
               arguments.getOpt[String](PARAM_OTHERWISE).map { _ + " (otherwise)" }
    s"SPLIT ${arguments.pretty(PARAM_DATASET)} INTO ${StringUtils.oxfordComma(names)}"
  }

  def title(arguments: Arguments) = 
    s"Split ${arguments.pretty(PARAM_DATASET)}"

  def process(arguments: Arguments, context: ExecutionContext)
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val inputDataset = context.artifact(datasetName)
                              .getOrElse {
                                throw new VizierException("Expected dataset parameter")
                              }
    val (partitions, preconditions) = 
      arguments.getList(PARAM_PARTITIONS)
               .foldLeft(Seq[(String, String)](), Seq[String]()) { 
                  case ((partitions, preconditions), partition) =>
                    val safeCondition = "(" + partition.get[String](PARAM_CONDITION) + ")"
                    val output = partition.get[String](PARAM_OUTPUT)
                    val fullCondition = preconditions :+ safeCondition
                    (
                      partitions :+ (output, fullCondition.mkString(" AND ")),
                      preconditions :+ ("(NOT "+safeCondition+")")
                    )
               }
    val outputs = partitions ++
                    arguments.getOpt[String](PARAM_OTHERWISE).map { 
                        (_, preconditions.mkString(" AND "))
                    }
    try { 
      for( (outputTable, outputCondition) <- outputs){
        logger.trace("Creating view for $outputTable / $outputCondition")

        context.outputDataset(
          outputTable,
          ViewConstructor(
            datasets = Map("INPUT" -> inputDataset.id): Map[String, Identifier],
            functions = Map.empty: Map[String, (Identifier, String, String)],
            variables = Map.empty: Map[String, Identifier],
            query = s"SELECT * FROM INPUT WHERE ${outputCondition}": String,
            projectId = context.projectId: Identifier,
            datasetSchemas = { _ => inputDataset.datasetSchema }: Identifier => Seq[StructField],
            variableTypes = { _ => assert(false, "No parameters in Split Dataset"); ??? }: Identifier => DataType,
          )
        )
        logger.trace("Rendering dataset summary")
        context.displayDataset(outputTable)
      }
    } catch { 
      case e: FormattedError => 
        context.error(e.getMessage())
    }
  }

  def predictProvenance(arguments: Arguments, properties: JsObject): ProvenancePrediction = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](PARAM_DATASET))
      .definitelyWrites((
          arguments.getList(PARAM_PARTITIONS).map { _.get[String](PARAM_OUTPUT) } ++ 
            arguments.getOpt[String](PARAM_OTHERWISE)
        ):_*)
      .andNothingElse
}