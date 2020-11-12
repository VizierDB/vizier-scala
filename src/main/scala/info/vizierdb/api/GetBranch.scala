package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Branch
import org.mimirdb.api.Request

case class GetBranchRequest(projectId: String, branchId: String)
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      Branch.lookup(projectId.toInt, branchId.toInt) match {
        case Some(branch) => RawJsonResponse(branch.describe)
        case None => NoSuchEntityResponse()
      }
    }
  } 
}