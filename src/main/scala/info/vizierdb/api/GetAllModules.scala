package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow, Module }
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import info.vizierdb.api.response._

case class GetAllModulesRequest(projectId: Identifier, branchId: Identifier, workflowId: Option[Identifier])
  extends Request
{
  def handle = 
  {
    DB.readOnly { implicit session => 
      val workflowMaybe: Option[Workflow] = 
        workflowId match {
          case Some(workflowIdActual) => 
            Workflow.lookup(projectId, branchId, workflowIdActual)
          case None => 
            Branch.lookup(projectId, projectId).map { _.head }
        } 
      workflowMaybe match {
        case Some(workflow) => RawJsonResponse(
          Module.describeAll(
            projectId = projectId,
            branchId = branchId, 
            workflowId = workflow.id,
            workflow.cellsAndModulesInOrder
          )
        )
        case None => NoSuchEntityResponse()
      }
    }
  } 
}