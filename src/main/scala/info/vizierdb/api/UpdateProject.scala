package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse

case class UpdateProject(
  projectId: Identifier,
  properties: Map[String, JsValue]
)
  extends Request
{
  def handle: Response = 
  {
    val project: Project = 
      DB.autoCommit { implicit s => 
        Project.lookup(projectId)
               .getOrElse { 
                 return NoSuchEntityResponse()
               }
               .update(
                  properties.get("name")
                            .map { _.as[String] }
                            .getOrElse { "Untitled Project" },
                  properties = properties
               )  
      }
    RawJsonResponse(
      project.summarize
    )
  } 
}

object UpdateProject
{
  implicit val format: Format[UpdateProject] = Json.format
}