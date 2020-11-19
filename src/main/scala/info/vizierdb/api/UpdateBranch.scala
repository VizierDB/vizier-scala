package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.Branch
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class UpdateBranch(
  projectId: Identifier,
  branchId: Identifier,
  properties: Map[String, JsValue]
)
  extends Request
{
  def handle: Response = 
  {
    DB.autoCommit { implicit s => 
      val branch: Branch = 
        Branch.lookup(projectId, branchId)
               .getOrElse { 
                 return NoSuchEntityResponse()
               }
               .updateProperties(
                  properties.get("name")
                            .map { _.as[String] }
                            .getOrElse { "Untitled Branch" },
                  properties = properties
               )  
      RawJsonResponse(
        branch.summarize
      )
    }
  } 
}

object UpdateBranch
{
  implicit val format: Format[UpdateBranch] = Json.format
}