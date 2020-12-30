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
import info.vizierdb.catalog.{ Project, Branch, Workflow }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class SourceBranch(
  branchId: Identifier,
  workflowId: Option[Identifier],
  moduleId: Option[Identifier]
)

object SourceBranch
{
  implicit val format: Format[SourceBranch] = Json.format
}

case class CreateBranch(
  projectId: Identifier,
  source: Option[SourceBranch],
  properties: JsObject
)
  extends Request
{
  def handle: Response = 
  {
    val branchName =
      properties.value
                .get("name")
                .map { _.as[String] }
                .getOrElse { "Untitled Branch" }
    DB.autoCommit { implicit s => 
      val (project, branch, workflow): (Project, Branch, Workflow) = 
        Project.lookup(projectId)
               .getOrElse { 
                 return NoSuchEntityResponse()
               }
               .createBranch(
                 name = branchName,
                 properties = properties,
                 fromBranch = source.map { _.branchId },
                 fromWorkflow = source.flatMap { _.workflowId }
               )
      RawJsonResponse(
        branch.summarize,
        status = Some(HttpServletResponse.SC_CREATED)
      )
    }
  } 
}

object CreateBranch
{
  implicit val format: Format[CreateBranch] = Json.format
}

