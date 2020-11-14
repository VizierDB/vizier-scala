package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse

case class CreateProject(
  properties: Map[String, JsValue]
)
  extends Request
{
  def handle = 
  {
    val project = 
      DB.autoCommit { implicit s => 
        Project.create(
          properties.get("name")
                    .map { _.as[String] }
                    .getOrElse { "Untitled Project" },
          properties = JsObject(properties)
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