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
import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.util.StupidReactJsonMap
import info.vizierdb.catalog.Project
import info.vizierdb.types._

class ProjectAPISpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init()

  val CREATE_PROJECT_NAME = "project-api-create-test"
  val DELETE_PROJECT_NAME = "project-api-delete-test"

  sequential

  "Create Projects" >> {
    val response = 
      CreateProject(StupidReactJsonMap(
        "thing" -> JsString("value"),
        "name" -> JsString(CREATE_PROJECT_NAME)
      )).handle

    DB.readOnly { implicit s => 
      val project = Project.get(
        response.data.as[JsObject].value("id").as[String].toLong
      )
      project.name must beEqualTo(CREATE_PROJECT_NAME)
      project.properties.value("thing").as[String] must beEqualTo("value")
    }
  }

  "Delete Projects" >> {
    val createResponse = 
      CreateProject(StupidReactJsonMap(
        "thing" -> JsString("value"),
        "name" -> JsString(DELETE_PROJECT_NAME)
      )).handle

    val id = createResponse.data.as[JsObject].value("id").as[String].toLong

    val deleteResponse = DeleteProjectHandler.handle(Map("projectId" -> JsNumber(id)))

    DB.readOnly { implicit s => 
      Project.lookup(id) must beNone
    }
  }

  "List Projects" >> {
    val response = 
      ListProjectsHandler.handle

    val projects = 
      response.data.as[Map[String, JsValue]]
        .apply("projects").as[Seq[Map[String, JsValue]]]
        .map { project => 
          project("id").as[String] -> 
            StupidReactJsonMap.decode(project("properties"))("name")
        }
        .toMap
        .mapValues { _.as[String] }

    projects.values must contain(CREATE_PROJECT_NAME)
    projects.values must not contain(DELETE_PROJECT_NAME)
  }
}

