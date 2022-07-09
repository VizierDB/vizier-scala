package info.vizierdb.serialized

import info.vizierdb.nativeTypes.JsObject
import info.vizierdb.nativeTypes.DateTime
import info.vizierdb.types.{ Identifier, ExecutionState }

case class WorkflowSummary(
  id: Identifier,
  createdAt: DateTime,
  action: String,
  actionModule: Option[Identifier],
  packageId: Option[String],
  commandId: Option[String],
)
{
  def toDescription(
    state: ExecutionState.T,
    modules: Seq[ModuleDescription],
    artifacts: Seq[ArtifactSummary],
    readOnly: Boolean,
  ): WorkflowDescription =
    WorkflowDescription(
      id = id,
      createdAt = createdAt,
      action = action,
      actionModule = actionModule,
      packageId = packageId,
      commandId = commandId,
      state = ExecutionState.translateToClassicVizier(state),
      statev2 = state,
      modules = modules,
      artifacts = artifacts,
      readOnly = readOnly,
    )
}

case class WorkflowDescription(
  id: Identifier,
  createdAt: DateTime,
  action: String,
  actionModule: Option[Identifier],
  packageId: Option[String],
  commandId: Option[String],
  state: Int,
  statev2: ExecutionState.T,
  modules: Seq[ModuleDescription],
  artifacts: Seq[ArtifactSummary],
  readOnly: Boolean,
)

