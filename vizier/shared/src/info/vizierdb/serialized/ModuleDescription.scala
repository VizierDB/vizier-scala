package info.vizierdb.serialized

import info.vizierdb.types.Identifier
import info.vizierdb.types.ExecutionState
import info.vizierdb.nativeTypes.JsObject

case class ModuleDescription(
  id: String,
  moduleId: Identifier,
  state: Int,
  statev2: ExecutionState.T,
  command: CommandDescription,
  text: String,
  toc: Option[TableOfContentsEntry],
  timestamps: Timestamps,
  artifacts: Seq[ArtifactSummary],
  deleted: Seq[String],
  inputs: Map[String,Identifier],
  outputs: ModuleOutputDescription,
  resultId: Option[Identifier],
)
