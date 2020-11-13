package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Project
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier

case class ListBranchesRequest(projectId: Identifier)
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      Project.lookup(projectId)
        match { 
          case Some(project) => 
            RawJsonResponse(
              Json.obj(
                "branches" -> JsArray(project.branches.map { _.summarize }),
                HATEOAS.LINKS -> HATEOAS(
                  HATEOAS.SELF -> VizierAPI.urls.listBranches(projectId.toString)
                )
              )
            )
          case None => NoSuchEntityResponse()
        }
    }
  }
}