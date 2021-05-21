package info.vizierdb.delta

import scalikejdbc.DBSession
import info.vizierdb.types._
import info.vizierdb.catalog.serialized.ModuleDescription

case class CellState(
  moduleId: String,
  resultId: Option[String],
  state: ExecutionState.T, 
  messageCount: Int
)
object CellState
{
  def apply(description: ModuleDescription): CellState =
    CellState(
      description.id,
      description.resultId,
      description.statev2,
      description.outputs.stdout.size + description.outputs.stderr.size
    )
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
        cells.patch(position, Seq(CellState(cell)), 0)
      )
      case UpdateCell(cell, position) => copy(cells = 
        cells.patch(position, Seq(CellState(cell)), 1)
      )
      case DeleteCell(position) => copy(cells = 
        cells.patch(position, Seq(), 1)
      )
      case UpdateCellState(position, newState) => copy(cells =
        cells.patch(position, Seq(
          cells(position).copy( state = newState )
        ), 1)
      )
      case AppendCellMessage(position, _, _) => copy(cells =
        cells.patch(position, Seq(
          cells(position).copy( messageCount = cells(position).messageCount + 1 )
        ), 1)
      )
      case _:AppendCellArtifact => this
      case _:DeleteCellArtifact => this
      case AdvanceResultId(position, resultId) => 
        val oldCell = cells(position)
        copy(cells = 
          cells.patch(position, Seq(
            oldCell.copy( 
              resultId = Some(resultId), 
              messageCount = 0
            )
          ), 1)
        )
    }

  def withWorkflowId(newWorkflowId: Identifier): WorkflowState = 
    copy(workflowId = newWorkflowId)
}