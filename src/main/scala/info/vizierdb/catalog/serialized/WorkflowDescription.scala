package info.vizierdb.catalog.serialized

import play.api.libs.json._
import java.time.ZonedDateTime
import info.vizierdb.util.HATEOAS

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
    datasets: Seq[JsObject],
    dataobjects: Seq[JsObject],
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

object WorkflowSummary
{
  implicit val format: Format[WorkflowSummary] = Json.format
}

case class WorkflowDescription(
  id: String,
  createdAt: String,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  state: Int,
  modules: Seq[ModuleDescription],
  datasets: Seq[JsObject],
  dataobjects: Seq[JsObject],
  readOnly: Boolean,
  tableOfContents: Option[Seq[TableOfContentsEntry]],
  links: HATEOAS.T
)

object WorkflowDescription
{
  implicit val format: Format[WorkflowDescription] = Json.format
}
