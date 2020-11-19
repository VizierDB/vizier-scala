package info.vizierdb.api

import scalikejdbc.DB
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import info.vizierdb.catalog.{ Branch, Project }
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class DeleteBranch(
  projectId: Identifier,
  branchId: Identifier
) extends Request
{
  def handle: Response =
  {
    DB.readOnly { implicit s => 
      val p = 
        Project.lookup(projectId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }
      if(p.activeBranchId == branchId){
        throw new IllegalArgumentException()
      }
    }
    DB.autoCommit { implicit s => 
      val b = 
        Branch.lookup(projectId = projectId, branchId = branchId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }
      b.deleteBranch
    }
    return NoContentResponse()
  }
}