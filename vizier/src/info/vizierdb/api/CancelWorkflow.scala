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
import info.vizierdb.catalog.{ Branch, Workflow }
import info.vizierdb.commands.Commands
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.api.response._
import info.vizierdb.api.handler._
import info.vizierdb.serializers._
import info.vizierdb.serialized

object CancelWorkflow
{
  def apply(
    projectId: Identifier,
    branchId: Identifier,
    workflowId: Option[Identifier] = None
  ): serialized.WorkflowDescription =
  {
    var workflow:Workflow =
      DB.readOnly { implicit s => 
        workflowId match { 
          case None => 
            Branch.getOption(projectId, branchId)
                  .getOrElse { ErrorResponse.noSuchEntity }
                  .head
          case Some(id) =>
            Workflow.getOption(projectId, branchId, id)
                    .getOrElse { ErrorResponse.noSuchEntity }
        }
      }

    // has to happen outside of a DB block
    Scheduler.abort(workflow.id)

    DB.autoCommit { implicit s => 
      workflow = workflow.abort
    }

    DB.readOnly { implicit s => 
      workflow.describe
    }
  } 
}

// object CancelWorkflow
// {
//   implicit val format: Format[CancelWorkflow] = Json.format
// }

