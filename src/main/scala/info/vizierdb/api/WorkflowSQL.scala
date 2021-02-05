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
package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow, Artifact, ArtifactSummary }
import org.mimirdb.api.{Request, Response}
import info.vizierdb.types.{ Identifier, ArtifactType }
import org.mimirdb.api.request.QueryMimirRequest
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.api.response._
import info.vizierdb.api.handler.Handler
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

object WorkflowSQLHandler
  extends Handler
  with LazyLogging
{
  def handle(
    pathParameters: Map[String, JsValue], 
    request: HttpServletRequest 
  ): Response =
  {
    val projectId = pathParameters("projectId").as[Long]
    val branchId = pathParameters("branchId").as[Long]
    val workflowId = pathParameters.get("workflowId").map { _.as[Long] }
    val query = request.getParameter("query")

    val (datasets, functions) = 
      DB.readOnly { implicit session => 
        val workflow: Workflow = 
          (workflowId match {
            case Some(workflowIdActual) => 
              Workflow.lookup(projectId, branchId, workflowIdActual)
            case None => 
              Branch.lookup(projectId, branchId).map { _.head }
          }).getOrElse {
            return NoSuchEntityResponse()
          }

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
                   .mapValues { _.nameInBackend }

        val functions = 
          artifacts.filter { _._2.t.equals(ArtifactType.FUNCTION) } 
                   .toMap
                   .mapValues { _.nameInBackend }

        /* return */ (datasets, functions)
      }

    logger.trace(s"Query Tail: $query")
    return QueryMimirRequest(
      input = None,
      views = Some(datasets),
      query = query,
      includeUncertainty = Some(true),
      includeReasons = None
    ).handle
  } 
}

