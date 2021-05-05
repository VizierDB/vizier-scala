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