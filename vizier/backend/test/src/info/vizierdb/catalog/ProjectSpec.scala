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
package info.vizierdb.catalog

import scalikejdbc._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.api.CreateBranch
import info.vizierdb.serialized
import play.api.libs.json._
import info.vizierdb.Vizier


class ProjectSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "branch projects" >> {
    val project = MutableProject("Data Project")

    project.append("dummy", "print")(
      "value" -> "thingie1"
    )
    project.append("dummy", "print")(
      "value" -> "thingie2"
    )
    project.waitUntilReady

    val response = CreateBranch(
      project.projectId, 
      Some(serialized.BranchSource(
        DB.readOnly { implicit s => project.activeBranch.id },
        workflowId = None, 
        moduleId = None
      )),
      serialized.PropertyList.toPropertyList(Map("name" -> JsString("Test")))
    )
    DB.autoCommit { implicit s => 
      Project.get(project.projectId).activateBranch(response.id)
    }

    DB.readOnly { implicit s => 
      project.activeBranch.id 
    } must beEqualTo(response.id)

    val workflow = project.head

    val modules = DB.readOnly { implicit s => workflow.modulesInOrder }

    modules.map { m => 
      m.packageId+"."+m.commandId 
    } must contain(exactly("dummy.print", "dummy.print"))
  }
}

