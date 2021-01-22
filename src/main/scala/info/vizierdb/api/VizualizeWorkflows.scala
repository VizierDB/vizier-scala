package info.vizierdb.api

import play.api.libs.json._
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types._
import info.vizierdb.api.response.StringResponse
import info.vizierdb.viztrails.graph.WorkflowTrace

case class VizualizeWorkflow(
  projectId: Identifier,
  branchId: Identifier,
  workflowId: Option[Identifier]
) extends Request
{
  def handle: Response =
  {
    StringResponse(
      WorkflowTrace(
        projectId,
        branchId, 
        workflowId
      ),
      "image/svg+xml"
    )
  }
}