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

    val deleteResponse = DeleteProject(id).handle

    DB.readOnly { implicit s => 
      Project.lookup(id) must beNone
    }
  }

  "List Projects" >> {
    val response = 
      ListProjectsRequest().handle

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