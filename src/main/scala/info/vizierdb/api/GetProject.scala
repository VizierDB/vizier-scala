package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import info.vizierdb.api.response._

case class GetProjectRequest(projectId: Identifier)
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      Project.lookup(projectId) match {
        case Some(project) => RawJsonResponse(project.describe)
        case None => NoSuchEntityResponse()
      }
    } 
  } 
}