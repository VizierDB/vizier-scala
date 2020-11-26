package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import info.vizierdb.util.StupidReactJsonMap

case class CreateProject(
  properties: StupidReactJsonMap.T
)
  extends Request
{
  def handle: RawJsonResponse = 
  {
    val saneProperties = StupidReactJsonMap.decode(properties)
    val project = 
      DB.autoCommit { implicit s => 
        Project.create(
          saneProperties.get("name")
                        .map { _.as[String] }
                        .getOrElse { "Untitled Project" },
          properties = JsObject(saneProperties)
        ) 
      }
    RawJsonResponse(
      project.summarize,
      status = Some(HttpServletResponse.SC_CREATED)
    )
  } 
}

object CreateProject
{
  implicit val format: Format[CreateProject] = Json.format
}