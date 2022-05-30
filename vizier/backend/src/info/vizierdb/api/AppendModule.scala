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
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.catalog.CatalogDB

object AppendModule
  extends Object
    with LazyLogging
{
  def apply(
    projectId: Identifier,
    branchId: Identifier,
    packageId: String,
    commandId: String,
    arguments: serialized.CommandArgumentList.T,
    workflowId: Option[Identifier] = None,
  ): serialized.WorkflowDescription = 
  {
    val command = Commands.get(packageId, commandId)

    val (workflow, workflowIdToAbort): (Workflow, Option[Identifier]) = 
      CatalogDB.withDB { implicit s => 
        logger.trace(s"Looking up branch $branchId")
        val branch:Branch =
          Branch.getOption(projectId, branchId)
                .getOrElse { ErrorResponse.noSuchEntity }
        val currentWorkflow = branch.head

        if(workflowId.isDefined) {
          if(branch.headId != workflowId.get){
            ErrorResponse.invalidRequest("Trying to modify an immutable workflow")
          }
        }

        logger.trace(s"Creating Module: $packageId.$commandId($arguments)")

        val module = 
          Module.make(
            packageId = packageId,
            commandId = commandId,
            arguments = command.argumentsFromPropertyList(arguments),
            revisionOfId = None
          )

        logger.debug(s"Appending Module: $module")
        
        /* return */ (
          branch.append(module)._2, 
          if(currentWorkflow.isRunning) { Some(currentWorkflow.id) } else { None }
        )
      }

    logger.trace(s"Scheduling ${workflow.id}")
    // The workflow must be scheduled AFTER the enclosing transaction finishes
    if(workflowIdToAbort.isDefined) {
      Scheduler.abort(workflowIdToAbort.get)
    }
    Scheduler.schedule(workflow)

    logger.trace("Building response")

    CatalogDB.withDBReadOnly { implicit s => 
      workflow.describe
    }()
  } 
}

