package info.vizierdb.delta

import info.vizierdb.types._

case class CellState(
  moduleId: Identifier,
  resultId: Option[Identifier],
  state: ExecutionState.T, 
  messageCount: Int
)

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