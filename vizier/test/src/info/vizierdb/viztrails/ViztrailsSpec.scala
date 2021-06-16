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
package info.vizierdb.viztrails

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import play.api.libs.json.{ Json, JsString }

import scalikejdbc._

import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.catalog.{ Project, Module }

class ViztrailsSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "create and recall projects" >> {
    val id = DB autoCommit { implicit session =>
      Project.create("Test Project").id
    }

    DB autoCommit { implicit session =>
      val project = Project.getOption(id).getOrElse {
                        ko("newly created project doesn't exist"); null
                      }
      project.name must be equalTo("Test Project")
      var activeBranch = project.activeBranch
      project.branches.map { _.id } must contain(activeBranch.id)

      val head = activeBranch.head 
      head.action must beEqualTo(ActionType.CREATE) 

      activeBranch = activeBranch.append(Module.make("dummy","print")(
        "value" -> "test" 
      ))._1
      activeBranch.head.id must not(beEqualTo(head.id))
      activeBranch.head.action must beEqualTo(ActionType.APPEND)

      activeBranch.head.length must beEqualTo(1) 
      val cells = activeBranch.head.cells.toSeq 
      cells must haveSize(1)
      cells(0).state must beEqualTo(ExecutionState.STALE) 
      cells(0).module.packageId must beEqualTo("dummy")
    }
  }
}

