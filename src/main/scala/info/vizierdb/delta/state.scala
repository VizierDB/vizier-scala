package info.vizierdb.delta

import scalikejdbc.DBSession
import info.vizierdb.types._
import info.vizierdb.catalog.serialized.ModuleDescription

case class CellState(
  moduleId: Identifier,
  resultId: Option[Identifier],
  state: ExecutionState.T, 
  messageCount: Int
)
{
  def moduleDescription(workflowId: Identifier, branchId: Identifier)(implicit session: DBSession): ModuleDescription = 
  {

    ???

    // Module.get(moduleId)
    //       .describe(
    //         cell = Cell(
    //           // WorkflowID and Position are not needed to describe the module
    //           workflowId = -1,
    //           position = -1,

    //           // 
    //           moduleId = 
    //         )
    //       )
  }
}

case class WorkflowState(
  branchId: Identifier,
  workflowId: Identifier,
  cells: Seq[CellState],
)
{
  def applyDelta(delta: WorkflowDelta): WorkflowState = 
    delta match {
      case InsertCell(cell, position) => copy(cells = 
        cells.patch(position, Seq(cell), 0)
      )
      case UpdateCell(cell, position) => copy(cells = 
        cells.patch(position, Seq(cell), 1)
      )
      case DeleteCell(position) => copy(cells = 
        cells.patch(position, Seq(), 1)
      )
    }

  def withWorkflowId(newWorkflowId: Identifier): WorkflowState = 
    copy(workflowId = newWorkflowId)
}