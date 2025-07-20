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
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.serialized.{ Property, PropertyList }
import info.vizierdb.catalog.Project
import info.vizierdb.types._
import info.vizierdb.serialized

class ProjectAPISpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init()

  val CREATE_PROJECT_NAME = "project-api-create-test"
  val DELETE_PROJECT_NAME = "project-api-delete-test"

  sequential

  "Create Projects" >> {
    val response: serialized.ProjectSummary = 
      CreateProject(PropertyList(
        "thing" -> JsString("value"),
        "name" -> JsString(CREATE_PROJECT_NAME)
      ))

    DB.readOnly { implicit s => 
      val project = Project.get(response.id)
      project.name must beEqualTo(CREATE_PROJECT_NAME)
      project.properties.value("thing").as[String] must beEqualTo("value")
    }
  }

  "Delete Projects" >> {
    val project: serialized.ProjectSummary = 
      CreateProject(PropertyList(
        "thing" -> JsString("value"),
        "name" -> JsString(DELETE_PROJECT_NAME)
      ))

    DeleteProject(projectId = project.id)

    DB.readOnly { implicit s => 
      Project.getOption(project.id) must beNone
    }
  }

  "List Projects" >> {
    val projects: Seq[serialized.ProjectSummary] = ListProjects().projects

    val projectNames = projects.map { project => 
                          PropertyList.lookup(project.properties, "name")
                                      .get
                                      .as[String]
                       }

    projectNames must contain(CREATE_PROJECT_NAME)
    projectNames must not contain(DELETE_PROJECT_NAME)
  }
}

