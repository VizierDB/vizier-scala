package info.vizierdb.api

import scalikejdbc.DB
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import info.vizierdb.catalog.Branch
import javax.servlet.http.HttpServletResponse
import info.vizierdb.api.response._
import info.vizierdb.viztrails.Scheduler

case class DeleteModule(
  projectId: Identifier,
  branchId: Identifier,
  moduleId: Identifier,
  workflowId: Option[Identifier] = None
) extends Request
{
  def handle: Response =
  {
    val workflow = 
      DB.autoCommit { implicit s => 
        val branch = 
          Branch.lookup(projectId = projectId, branchId = branchId)
                 .getOrElse { 
                    return NoSuchEntityResponse()
                 }
        if(workflowId.isDefined) {
          if(branch.headId != workflowId.get){
            return VizierErrorResponse("Invalid", "Trying to modify an immutable workflow")
          }
        }
        val cellToDelete =
          branch.head.cellByModuleId(moduleId)
                .getOrElse {
                    return NoSuchEntityResponse()
                }
        branch.delete(cellToDelete.position)._2
      }

    // The workflow must be scheduled AFTER the enclosing transaction finishes
    Scheduler.schedule(workflow.id)

    return NoContentResponse()
  }
}