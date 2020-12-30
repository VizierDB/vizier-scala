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
import info.vizierdb.catalog.{ Branch, Workflow, Module }
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import info.vizierdb.api.response._

case class GetAllModulesRequest(projectId: Identifier, branchId: Identifier, workflowId: Option[Identifier])
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      val workflowMaybe: Option[Workflow] = 
        workflowId match {
          case Some(workflowIdActual) => 
            Workflow.lookup(projectId, branchId, workflowIdActual)
          case None => 
            Branch.lookup(projectId, projectId).map { _.head }
        } 
      workflowMaybe match {
        case Some(workflow) => RawJsonResponse(
          Module.describeAll(
            projectId = projectId,
            branchId = branchId, 
            workflowId = workflow.id,
            workflow.cellsAndModulesInOrder
          )
        )
        case None => NoSuchEntityResponse()
      }
    }
  } 
}

