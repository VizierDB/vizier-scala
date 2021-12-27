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
package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow, Artifact, ArtifactSummary }
import info.vizierdb.types.{ Identifier, ArtifactType }
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.api.response._
import info.vizierdb.api.handler.{ Handler, ClientConnection }
import info.vizierdb.spark.caveats.{ QueryWithCaveats, DataContainer }
import info.vizierdb.Vizier

object WorkflowSQL
  extends Object
  with LazyLogging
{
  def apply(
    projectId: Identifier,
    branchId: Identifier,
    query: Option[String],
    workflowId: Option[Identifier] = None
  ): DataContainer =
  {
    if(Vizier.config.serverMode()){
      ErrorResponse.invalidRequest(
        "Workflow SQL is disabled in server mode.",
      )
    }

    val (datasets, functions) = 
      DB.readOnly { implicit session => 
        val workflow: Workflow = 
          (workflowId match {
            case Some(workflowIdActual) => 
              Workflow.getOption(projectId, branchId, workflowIdActual)
            case None => 
              Branch.getOption(projectId, branchId).map { _.head }
          }).getOrElse { ErrorResponse.noSuchEntity }

        val artifacts: Seq[(String, ArtifactSummary)] = 
          workflow.outputArtifacts
                  .map { a => a.userFacingName -> a.getSummary }
                  // if summary returns None, this is a delete
                  .flatMap { 
                    case (_, None) => None
                    case (name, Some(summary)) => Some(name -> summary) 
                  }

        val datasets = 
          artifacts.filter { _._2.t.equals(ArtifactType.DATASET) }
                   .toMap

        val functions = 
          artifacts.filter { _._2.t.equals(ArtifactType.FUNCTION) } 
                   .toMap

        /* return */ (datasets, functions)
      }

    logger.trace(s"Query Tail: ${query.get}")

    QueryWithCaveats(
      query = query.get,
      views = 
        DB.readOnly { implicit s => 
          datasets.mapValues { a => Artifact.get(a.id) }
                  .mapValues { a => val df = a.dataframe; { () => df } }
        },
      includeCaveats = true,
    )
  } 
}

