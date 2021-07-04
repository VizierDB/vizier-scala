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
import info.vizierdb.catalog.Project
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import info.vizierdb.serialized.PropertyList

object UpdateProject
{
  def apply(
    projectId: Identifier,
    properties: Option[PropertyList.T] = None,
    defaultBranch: Option[Identifier] = None
  ): Response =
  {
    val project: Project = 
      DB.autoCommit { implicit s => 
        var project = Project.getOption(projectId)
                             .getOrElse { 
                               return NoSuchEntityResponse()
                             }
        if(defaultBranch.isDefined){
          if(project.branchIds contains defaultBranch.get){
            project = project.activateBranch(defaultBranch.get)
          } else {
            return NoSuchEntityResponse()
          }
        }
        if(properties.isDefined){
          val saneProperties:Map[String, JsValue] = PropertyList.toMap(properties.get)
          project = project.updateProperties(
                              saneProperties.get("name")
                                            .map { _.as[String] }
                                            .getOrElse { "Untitled Project" },
                              properties = saneProperties
                            )
        }
        /* return */ project
      }
    RawJsonResponse(
      project.summarize
    )
  } 
}
