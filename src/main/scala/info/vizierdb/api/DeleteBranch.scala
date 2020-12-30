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
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import info.vizierdb.catalog.{ Branch, Project }
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class DeleteBranch(
  projectId: Identifier,
  branchId: Identifier
) extends Request
{
  def handle: Response =
  {
    DB.readOnly { implicit s => 
      val p = 
        Project.lookup(projectId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }
      if(p.activeBranchId == branchId){
        throw new IllegalArgumentException()
      }
    }
    DB.autoCommit { implicit s => 
      val b = 
        Branch.lookup(projectId = projectId, branchId = branchId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }
      b.deleteBranch
    }
    return NoContentResponse()
  }
}

