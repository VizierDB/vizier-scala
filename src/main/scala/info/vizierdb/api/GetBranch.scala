package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Branch
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import info.vizierdb.api.response._

case class GetBranchRequest(projectId: Identifier, branchId: Identifier)
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      Branch.lookup(projectId, branchId) match {
        case Some(branch) => RawJsonResponse(branch.describe)
        case None => NoSuchEntityResponse()
      }
    }
  } 
}