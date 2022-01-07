package info.vizierdb.serialized

import info.vizierdb.shared.HATEOAS
import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.DateTime

case class BranchSummary(
  id: Identifier,
  createdAt: DateTime,
  lastModifiedAt: DateTime,
  sourceBranch: Option[Identifier],
  sourceWorkflow: Option[Identifier],
  sourceModule: Option[Identifier],
  isDefault: Boolean,
  properties: PropertyList.T,
  links: HATEOAS.T
)
{
  def toDescription(workflows: Seq[WorkflowSummary]) =
    BranchDescription(
      id = id,
      createdAt = createdAt,
      lastModifiedAt = lastModifiedAt,
      sourceBranch = sourceBranch,
      sourceWorkflow = sourceWorkflow,
      sourceModule = sourceModule,
      isDefault = isDefault,
      properties = properties,
      links = links,
      workflows = workflows
    )

  def name: String =
    properties.find { _.key == "name" }
              .map { _.value.as[String] }
              .getOrElse { "Untitled Branch" }
}

case class BranchDescription(
  id: Identifier,
  createdAt: DateTime,
  lastModifiedAt: DateTime,
  sourceBranch: Option[Identifier],
  sourceWorkflow: Option[Identifier],
  sourceModule: Option[Identifier],
  isDefault: Boolean,
  properties: PropertyList.T,
  links: HATEOAS.T,
  workflows: Seq[WorkflowSummary]
)
{
  def name: String =
    properties.find { _.key == "name" }
              .map { _.value.as[String] }
              .getOrElse { "Untitled Branch" }
}

case class BranchList(
  branches: Seq[BranchSummary],
  links: HATEOAS.T
)