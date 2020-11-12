package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow }
import org.mimirdb.api.Request

case class GetWorkflowRequest(projectId: String, branchId: String, workflowId: Option[String])
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      val workflowMaybe: Option[Workflow] = 
        workflowId match {
          case Some(workflowIdActual) => 
            Workflow.lookup(projectId.toInt, branchId.toInt, workflowIdActual.toInt)
          case None => 
            Branch.lookup(projectId.toInt, branchId.toInt).map { _.head }
        } 
      workflowMaybe match {
        case Some(workflow) => RawJsonResponse(workflow.describe)
        case None => NoSuchEntityResponse()
      }
    }
  } 
}