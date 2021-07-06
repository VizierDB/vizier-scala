package info.vizierdb.serialized

import info.vizierdb.shared.HATEOAS
import info.vizierdb.nativeTypes.JsObject
import info.vizierdb.nativeTypes.DateTime

case class WorkflowSummary(
  id: String,
  createdAt: DateTime,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  links: HATEOAS.T
)
{
  def toDescription(
    state: Int,
    modules: Seq[ModuleDescription],
    datasets: Seq[ArtifactSummary],
    dataobjects: Seq[ArtifactSummary],
    readOnly: Boolean,
    tableOfContents: Option[Seq[TableOfContentsEntry]],
    newLinks: HATEOAS.T
  ): WorkflowDescription =
    WorkflowDescription(
      id = id,
      createdAt = createdAt,
      action = action,
      packageId = packageId,
      commandId = commandId,
      state = state,
      modules = modules,
      datasets = datasets,
      dataobjects = dataobjects,
      readOnly = readOnly,
      tableOfContents = tableOfContents,
      links = HATEOAS.merge(links, newLinks)
    )
}

case class WorkflowDescription(
  id: String,
  createdAt: DateTime,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  state: Int,
  modules: Seq[ModuleDescription],
  datasets: Seq[ArtifactSummary],
  dataobjects: Seq[ArtifactSummary],
  readOnly: Boolean,
  tableOfContents: Option[Seq[TableOfContentsEntry]],
  links: HATEOAS.T
)

