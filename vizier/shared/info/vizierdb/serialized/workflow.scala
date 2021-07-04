package info.vizierdb.serialized

import java.time.ZonedDateTime
import info.vizierdb.shared.HATEOAS
import info.vizierdb.nativeTypes.JsObject
import info.vizierdb.catalog.ArtifactSummary

case class WorkflowSummary(
  id: String,
  createdAt: String,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  links: HATEOAS.T
)
{
  def toDescription(
    state: Int,
    modules: Seq[ModuleDescription],
    datasets: Map[String,ArtifactSummary],
    dataobjects: Map[String,ArtifactSummary],
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
  createdAt: String,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  state: Int,
  modules: Seq[ModuleDescription],
  datasets: Map[String,ArtifactSummary],
  dataobjects: Map[String,ArtifactSummary],
  readOnly: Boolean,
  tableOfContents: Option[Seq[TableOfContentsEntry]],
  links: HATEOAS.T
)

