package info.vizierdb.serialized

import info.vizierdb.shared.HATEOAS
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
  timestamps: Timestamps,
  datasets: Seq[ArtifactSummary],
  charts: Seq[ArtifactSummary],
  artifacts: Seq[ArtifactSummary],
  outputs: ModuleOutputDescription,
  resultId: Option[String],
  links: HATEOAS.T
)
