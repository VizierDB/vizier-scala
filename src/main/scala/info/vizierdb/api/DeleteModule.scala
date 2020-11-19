package info.vizierdb.api

import scalikejdbc.DB
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import info.vizierdb.catalog.Branch
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._

case class DeleteModule(
  projectId: Identifier,
  branchId: Identifier,
  moduleId: Identifier
) extends Request
{
  def handle: Response =
  {
    DB.autoCommit { implicit s => 
      val b = 
        Branch.lookup(projectId = projectId, branchId = branchId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }
      val cellToDelete =
        b.head.cellByModuleId(moduleId)
              .getOrElse {
                  return NoSuchEntityResponse()
              }
      b.delete(cellToDelete.position)
    }
    return NoContentResponse()
  }
}