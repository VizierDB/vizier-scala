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
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Module, Workflow }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.commands.Commands

case class ReplaceModule(
  projectId: Identifier,
  branchId: Identifier,
  modulePosition: Int,
  workflowId: Option[Identifier],
  packageId: String,
  commandId: String,
  arguments: JsArray
)
  extends Request
{
  def handle: Response = 
  {
    val workflow: Workflow = 
      DB.autoCommit { implicit s => 
        val branch: Branch = 
          Branch.getOption(projectId, branchId)
                 .getOrElse { 
                   return NoSuchEntityResponse()
                 }
        val cell =
          branch.head
                .cellByPosition(modulePosition)
                .getOrElse { 
                   return NoSuchEntityResponse()
                 }

        if(workflowId.isDefined) {
          if(branch.headId != workflowId.get){
            return VizierErrorResponse("Invalid", "Trying to modify an immutable workflow")
          }
        }

        val command = Commands.get(packageId, commandId)

        val module = 
          Module.make(
            packageId = packageId,
            commandId = commandId,
            arguments = command.decodeReactArguments(arguments),
            revisionOfId = Some(cell.moduleId)
          )
          
        /* return */ branch.update(cell.position, module)._2
    }

    Scheduler.schedule(workflow)

    DB.readOnly { implicit s => 
      RawJsonResponse(
        Json.toJson(
          workflow.describe
        )
      )
    }
  } 
}

object ReplaceModule
{
  implicit val format: Format[ReplaceModule] = Json.format
}

