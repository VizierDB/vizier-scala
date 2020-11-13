package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow, Cell }
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier

case class GetModuleRequest(projectId: Identifier, branchId: Identifier, workflowId: Option[Identifier], moduleId: Identifier)
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
      val cellMaybe: Option[Cell] = 
        workflowMaybe.flatMap { _.cellByModuleId(moduleId) }
      cellMaybe match {
        case Some(cell) => RawJsonResponse(
          cell.module.describe(cell, projectId, branchId, workflowMaybe.get.id)
        )
        case None => NoSuchEntityResponse()
      }
    }
  } 
}