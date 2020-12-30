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
import info.vizierdb.commands.Commands
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.viztrails.Scheduler

case class AppendModule(
  projectId: Identifier,
  branchId: Identifier,
  workflowId: Option[Identifier],
  packageId: String,
  commandId: String,
  arguments: JsArray
)
  extends Request
    with LazyLogging
{
  def handle: Response = 
  {
    val command = Commands.get(packageId, commandId)

    val workflow: Workflow = 
      DB.autoCommit { implicit s => 
        logger.trace(s"Looking up branch $branchId")
        val branch:Branch =
          Branch.lookup(projectId, branchId)
                .getOrElse { 
                   return NoSuchEntityResponse()
                }

        if(workflowId.isDefined) {
          if(branch.headId != workflowId.get){
            return VizierErrorResponse("Invalid", "Trying to modify an immutable workflow")
          }
        }

        logger.trace(s"Creating Module: $packageId.$commandId($arguments)")

        val module = 
          Module.make(
            packageId = packageId,
            commandId = commandId,
            arguments = command.decodeReactArguments(arguments),
            revisionOfId = None
          )

        logger.debug(s"Appending Module: $module")
        
        /* return */ branch.append(module)._2

      }

    logger.trace(s"Scheduling ${workflow.id}")
    // The workflow must be scheduled AFTER the enclosing transaction finishes
    Scheduler.schedule(workflow.id)

    logger.trace("Building response")

    DB.readOnly { implicit s => 
      RawJsonResponse(
        workflow.describe
      )
    }
  } 
}

object AppendModule
{
  implicit val format: Format[AppendModule] = Json.format
}

