package info.vizierdb.api

import scalikejdbc.DB
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import info.vizierdb.catalog.Project
import javax.servlet.http.HttpServletResponse

case class DeleteProject(
  projectId: Identifier
) extends Request
{
  def handle: Response =
  {
    DB.autoCommit { implicit s => 
      val p = 
        Project.lookup(projectId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }
      p.deleteProject
    }
    return NoContentResponse()
  }
}