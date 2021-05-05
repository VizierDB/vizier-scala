package info.vizierdb.delta

import info.vizierdb.catalog._
import info.vizierdb.catalog.serialized._

sealed trait WorkflowDelta

case class InsertCell(cell: CellState, position: Int) extends WorkflowDelta
case class UpdateCell(cell: CellState, position: Int) extends WorkflowDelta
case class DeleteCell(position: Int) extends WorkflowDelta
