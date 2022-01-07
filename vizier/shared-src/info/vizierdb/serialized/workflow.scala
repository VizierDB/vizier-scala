package info.vizierdb.serialized

import info.vizierdb.shared.HATEOAS
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
  links: HATEOAS.T
)
{
  def toDescription(
    state: ExecutionState.T,
    modules: Seq[ModuleDescription],
    datasets: Seq[ArtifactSummary],
    dataobjects: Seq[ArtifactSummary],
    readOnly: Boolean,
    newLinks: HATEOAS.T
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
      datasets = datasets,
      dataobjects = dataobjects,
      readOnly = readOnly,
      links = HATEOAS.merge(links, newLinks)
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
  datasets: Seq[ArtifactSummary],
  dataobjects: Seq[ArtifactSummary],
  readOnly: Boolean,
  links: HATEOAS.T
)

