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
package info.vizierdb.commands.sql

import play.api.libs.json._
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.types._
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import org.mimirdb.api.request.CreateViewRequest
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.spark.InjectedSparkSQL
import org.mimirdb.api.MimirAPI
import info.vizierdb.catalog.ArtifactSummary
import info.vizierdb.viztrails.ProvenancePrediction

object Query extends Command
  with LazyLogging
{

  val TEMPORARY_DATASET = "temporary_dataset"

  def name: String = "SQL Query"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = "source", language = "sql", name = "SQL Code"),
    StringParameter(id = "output_dataset", name = "Output Dataset", required = false)
  )
  def format(arguments: Arguments): String = 
    s"${arguments.pretty("source")} TO ${arguments.pretty("output_dataset")}"
  def title(arguments: Arguments): String =
    arguments.getOpt[String]("output_dataset")
             .map { "SELECT into " + _ }
             .getOrElse { "SQL Query" }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val scope = context.allDatasets
    val functions = context.scope
                           .toSeq
                           .filter { case (name, summary) => 
                                      summary.t == ArtifactType.FUNCTION }
                           .map { case (name, summary:ArtifactSummary) => name -> summary.id }
                           .toMap
    val datasetName = arguments.getOpt[String]("output_dataset").getOrElse { TEMPORARY_DATASET }
    val (dsName, dsId) = context.outputDataset(datasetName)
    val query = arguments.get[String]("source")

    logger.debug(s"$scope : $query")

    try { 
      logger.trace("Creating view")
      val response = CreateViewRequest(
        input = scope.mapValues { _.nameInBackend }, 
        functions = Some(functions.mapValues { _.toString }),
        query = query, 
        resultName = Some(dsName),
        properties = None
      ).handle

      logger.trace("View created; Gathering dependencies")
      for(dep <- response.dependencies){
        context.inputs.put(dep, scope(dep).id)
      }
      for(dep <- response.functions){
        context.inputs.put(dep, functions(dep))
      }

      logger.trace("Rendering dataset summary")
      context.displayDataset(datasetName)
    } catch { 
      case e: org.mimirdb.api.FormattedError => 
        context.error(e.response.errorMessage)
    }
  }

  lazy val parser = InjectedSparkSQL(MimirAPI.sparkSession)

  def computeDependencies(sql: String): Seq[String] =
  {
    val (views, functions) = parser.getDependencies(sql)
    return views.toSeq ++ functions.toSeq
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) =
    try {
      ProvenancePrediction
        .definitelyReads(
          computeDependencies(arguments.get[String]("source")):_*
        )
        .definitelyWrites(
          arguments.getOpt[String]("output_dataset").getOrElse { TEMPORARY_DATASET }
        )
        .andNothingElse
    } catch {
      case t:Throwable => 
        ProvenancePrediction.default
    }
}

