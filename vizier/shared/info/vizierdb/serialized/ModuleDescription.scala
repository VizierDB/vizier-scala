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
  datasets: Seq[JsObject],
  charts: Seq[JsObject],
  artifacts: Seq[JsObject],
  outputs: ModuleOutputDescription,
  resultId: Option[String],
  links: HATEOAS.T
)
