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
import info.vizierdb.shared.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import info.vizierdb.api.response._
import info.vizierdb.api.response.RawJsonResponse
import info.vizierdb.api.handler.DeterministicHandler
import info.vizierdb.serialized

object ListProjects
{
  def apply(): serialized.ProjectList =
    serialized.ProjectList(
      projects =
        DB.readOnly { implicit session => 
          Project.list.map { _.summarize }
        },
      links = HATEOAS(
        HATEOAS.SELF            -> VizierAPI.urls.listProjects,
        HATEOAS.PROJECT_CREATE  -> VizierAPI.urls.createProject,
        HATEOAS.PROJECT_IMPORT  -> VizierAPI.urls.importProject
      )
    )
}

