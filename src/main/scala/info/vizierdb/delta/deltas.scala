package info.vizierdb.delta

import info.vizierdb.catalog._
import info.vizierdb.catalog.serialized._

sealed trait WorkflowDelta

case class InsertModule(module: ModuleDescription, position: Int) extends WorkflowDelta
case class UpdateModule(module: ModuleDescription, position: Int) extends WorkflowDelta
case class DeleteModule(position: Int) extends WorkflowDelta
