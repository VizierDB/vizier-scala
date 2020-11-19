package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.Request
import info.vizierdb.api.response._

case class ListProjectsRequest()
  extends Request
{
  def handle = 
  {
    RawJsonResponse(
      Json.obj(
        "projects" -> 
          DB.readOnly { implicit session => 
            Project.list.map { _.summarize }
          },
        HATEOAS.LINKS -> HATEOAS(
          HATEOAS.SELF            -> VizierAPI.urls.listProjects,
          HATEOAS.PROJECT_CREATE  -> VizierAPI.urls.createProject,
          HATEOAS.PROJECT_IMPORT  -> VizierAPI.urls.importProject
        )
      )
    )
  } 
}