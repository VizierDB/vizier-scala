package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.Request

case class GetProjectRequest(projectId: String)
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      Project.lookup(projectId.toInt) match {
        case Some(project) => RawJsonResponse(project.describe)
        case None => NoSuchEntityResponse()
      }
    } 
  } 
}