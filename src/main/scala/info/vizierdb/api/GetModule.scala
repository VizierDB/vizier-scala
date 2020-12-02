package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Branch, Workflow, Cell }
import org.mimirdb.api.Request
import info.vizierdb.types.Identifier
import info.vizierdb.api.response._
import info.vizierdb.viztrails.Provenance

case class GetModuleRequest(
  projectId: Identifier, 
  branchId: Identifier, 
  workflowId: Option[Identifier], 
  modulePosition: Int
)
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
        workflowMaybe.flatMap { _.cellByPosition(modulePosition) }
      cellMaybe match {
        case Some(cell) => RawJsonResponse(
          cell.module.describe(
            cell = cell, 
            projectId = projectId, 
            branchId = branchId, 
            workflowId = workflowMaybe.get.id,
            artifacts = Provenance.getRefScope(cell).values.toSeq
          )
        )
        case None => NoSuchEntityResponse()
      }
    }
  } 
}