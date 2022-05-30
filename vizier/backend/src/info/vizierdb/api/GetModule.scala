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
import info.vizierdb.catalog.{ Branch, Workflow, Cell }
import info.vizierdb.types.Identifier
import info.vizierdb.api.response._
import info.vizierdb.viztrails.ScopeSummary
import info.vizierdb.api.handler.SimpleHandler
import info.vizierdb.serializers._
import info.vizierdb.serialized
import info.vizierdb.catalog.CatalogDB

object GetModule
{
  def apply(
    projectId: Identifier,
    branchId: Identifier,
    modulePosition: Int,
    workflowId: Option[Identifier] = None
  ): serialized.ModuleDescription =
  {
    val ret: () => serialized.ModuleDescription =
      CatalogDB.withDBReadOnly { implicit session => 
        val workflowMaybe: Option[Workflow] = 
          workflowId match {
            case Some(workflowIdActual) => 
              Workflow.getOption(projectId, branchId, workflowIdActual)
            case None => 
              Branch.getOption(projectId, projectId).map { _.head }
          } 
        val cellMaybe: Option[Cell] = 
          workflowMaybe.flatMap { _.cellByPosition(modulePosition) }
        cellMaybe match {
          case Some(cell) => 
              cell.module.describe(
                cell = cell, 
                result = cell.result,
                messages = cell.messages.toSeq,
                outputs = cell.outputArtifacts.toSeq,
                inputs = cell.inputs.flatMap { _.tuple },
                projectId = projectId, 
                branchId = branchId, 
                workflowId = workflowMaybe.get.id,
              )
          case None => ErrorResponse.noSuchEntity
        }
      }
    return ret()
  } 
}

