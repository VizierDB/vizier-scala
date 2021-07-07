package info.vizierdb.serialized

import info.vizierdb.types._

case class BranchSource(
  branchId: Identifier,
  workflowId: Option[Identifier],
  moduleId: Option[Identifier]
)