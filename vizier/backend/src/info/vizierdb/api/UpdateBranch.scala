/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
import info.vizierdb.catalog.Branch
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.util.StupidReactJsonMap
import info.vizierdb.catalog.CatalogDB

object UpdateBranch
{
  def apply(
    projectId: Identifier,
    branchId: Identifier,
    properties: serialized.PropertyList.T
  ): serialized.BranchSummary =
  {
    val saneProperties = serialized.PropertyList.toMap(properties)
    CatalogDB.withDB { implicit s => 
      val branch: Branch = 
        Branch.getOption(projectId, branchId)
               .getOrElse { ErrorResponse.noSuchEntity }
               .updateProperties(
                  saneProperties.get("name")
                                .map { _.as[String] }
                                .getOrElse { "Untitled Branch" },
                  properties = saneProperties
               )  
      branch.summarize
    }
  } 
}

