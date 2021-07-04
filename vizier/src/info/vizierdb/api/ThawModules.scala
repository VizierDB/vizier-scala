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
import info.vizierdb.catalog.{ Branch, Workflow, Module }
import info.vizierdb.commands.Commands
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.api.handler.SimpleHandler
import info.vizierdb.serializers._

object ThawModules
  extends Object
  with LazyLogging
{
  def apply(thawUptoHere: Boolean)(
    projectId: Identifier,
    branchId: Identifier,
    modulePosition: Int,
    workflowId: Option[Identifier] = None,
  ): Response =
  {
    val workflow: Workflow = 
      DB.autoCommit { implicit s => 
        logger.trace(s"Looking up branch $branchId")
        val branch:Branch =
          Branch.getOption(projectId, branchId)
                .getOrElse { 
                   return NoSuchEntityResponse()
                }

        if(workflowId.isDefined) {
          if(branch.headId != workflowId.get){
            return VizierErrorResponse("Invalid", "Trying to modify an immutable workflow")
          }
        }

        if(thawUptoHere){
          /* return */ branch.thawUpto(modulePosition)._2
        } else {
          /* return */ branch.thawOne(modulePosition)._2
        }

      }

    logger.trace(s"Scheduling ${workflow.id}")
    // The workflow must be scheduled AFTER the enclosing transaction finishes
    Scheduler.schedule(workflow.id)

    logger.trace("Building response")

    DB.readOnly { implicit s => 
      RawJsonResponse(
        Json.toJson(
          workflow.describe
        )
      )
    }
  } 
}

