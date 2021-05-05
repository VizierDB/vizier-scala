package info.vizierdb.delta

import info.vizierdb.catalog._
import info.vizierdb.catalog.serialized._

sealed trait WorkflowDelta

case class InsertModule(module: CellState, position: Int) extends WorkflowDelta
case class UpdateModule(module: CellState, position: Int) extends WorkflowDelta
case class DeleteModule(position: Int) extends WorkflowDelta
