/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
package info.vizierdb.commands.sql

import play.api.libs.json._
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import org.mimirdb.api.request.CreateViewRequest
import com.typesafe.scalalogging.LazyLogging

object Query extends Command
  with LazyLogging
{
  def name: String = "SQL Query"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = "source", language = "sql", name = "SQL Code"),
    StringParameter(id = "output_dataset", name = "Output Dataset", required = false)
  )
  def format(arguments: Arguments): String = 
    s"${arguments.pretty("source")} TO ${arguments.pretty("output_dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val scope = context.allDatasets
    val datasetName = arguments.getOpt[String]("output_dataset").getOrElse { "temporary_dataset" }
    val (dsName, dsId) = context.outputDataset(datasetName)
    val query = arguments.get[String]("source")

    logger.debug(s"$scope : $query")

    try { 
      logger.trace("Creating view")
      val response = CreateViewRequest(
        input = scope.mapValues { _.nameInBackend }, 
        functions = None,
        query = query, 
        resultName = Some(dsName),
        properties = None
      ).handle

      logger.trace("View created; Gathering dependencies")
      for(dep <- response.dependencies){
        context.inputs.put(dep, scope(dep).id)
      }

      logger.trace("Rendering dataset summary")
      context.displayDataset(datasetName)
    } catch { 
      case e: org.mimirdb.api.FormattedError => 
        context.error(e.response.errorMessage)
    }

  }
}

