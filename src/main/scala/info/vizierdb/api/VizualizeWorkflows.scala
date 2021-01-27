package info.vizierdb.api

import play.api.libs.json._
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types._
import info.vizierdb.api.response.StringResponse
import info.vizierdb.viztrails.graph.WorkflowTrace
import info.vizierdb.api.handler._

object VizualizeWorkflow
  extends SimpleHandler
{
  def handle(pathParameters: Map[String, JsValue]): Response =
  {
    val projectId = pathParameters("projectId").as[Long]
    val branchId = pathParameters("branchId").as[Long]
    val workflowId = pathParameters("workflowId").asOpt[Long]
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