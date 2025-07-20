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
import info.vizierdb.catalog.Artifact
import info.vizierdb.types.ArtifactType
import info.vizierdb.commands.sql.Query
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.ViewConstructor
import org.apache.spark.sql.types.DataType
import info.vizierdb.types.Identifier


trait SQLTemplateCommand 
  extends Command
  with LazyLogging
{
  val PARAM_OUTPUT_DATASET = "output_dataset"

  val DEFAULT_DS_NAME = Query.TEMPORARY_DATASET
  
  def templateParameters: Seq[Parameter]
  def parameters = 
    templateParameters :+
      StringParameter(id = PARAM_OUTPUT_DATASET, name = "Output Dataset", required = false)

  def query(
    arguments: Arguments, 
    context: ExecutionContext
  ): (Map[String, Artifact], String)

  def process(
    arguments: Arguments, 
    context: ExecutionContext
  ) {
    // Query has to come before we allocate the output dataset name
    val (deps, sql)  = query(arguments, context)
    val outputDatasetName = arguments.getOpt[String](PARAM_OUTPUT_DATASET)
                                     .getOrElse { DEFAULT_DS_NAME }
    logger.debug(s"$sql")

    try { 
      logger.trace("Creating view")
      context.outputDataset(
        outputDatasetName,
        ViewConstructor(
          datasets = deps.mapValues { _.id },
          functions = Map.empty,
          variables = Map.empty,
          query = sql, 
          projectId = context.projectId,
          datasetSchemas = deps.map { case (_, d) => d.id -> d.datasetSchema }.toMap.apply(_),
          variableTypes = { _: Identifier => assert(false, "Template Commands can't take parameters"); null:DataType}
        )
      )

      logger.trace("Rendering dataset summary")
      context.displayDataset(outputDatasetName)
    } catch { 
      case e: info.vizierdb.api.FormattedError => 
        context.error(e.getMessage)
    }
  }
}
