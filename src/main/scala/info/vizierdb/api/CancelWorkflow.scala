package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow }
import info.vizierdb.commands.Commands
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types.Identifier
import javax.servlet.http.HttpServletResponse
import info.vizierdb.viztrails.Scheduler

case class CancelWorkflow(
  projectId: Identifier,
  branchId: Identifier,
  workflowId: Option[Identifier]
)
  extends Request
{
  def handle: Response = 
  {
    DB.readOnly { implicit s => 
      val workflow:Workflow =
        workflowId match { 
          case None => 
            Branch.lookup(projectId, branchId)
                  .getOrElse {
                     return NoSuchEntityResponse()
                  }
                  .head
          case Some(id) =>
            Workflow.lookup(projectId, branchId, id)
                    .getOrElse {
                       return NoSuchEntityResponse()
                    }
        }
      workflow.abort
      RawJsonResponse(
        workflow.describe
      )
    }
  } 
}

object CancelWorkflow
{
  implicit val format: Format[CancelWorkflow] = Json.format
}