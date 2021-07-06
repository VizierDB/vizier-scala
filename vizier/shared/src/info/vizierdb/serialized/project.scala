package info.vizierdb.serialized

import info.vizierdb.shared.HATEOAS
import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.DateTime

case class ProjectSummary(
  id: Identifier,
  createdAt: DateTime,
  lastModifiedAt: DateTime,
  defaultBranch: Identifier,
  properties: PropertyList.T,
  links: HATEOAS.T
)
{
  def toDescription(branches: Seq[BranchSummary]) =
    ProjectDescription(
      id = id,
      createdAt = createdAt,
      lastModifiedAt = lastModifiedAt,
      defaultBranch = defaultBranch,
      properties = properties,
      links = links,
      branches = branches
    )
}

case class ProjectDescription(
  id: Identifier,
  createdAt: DateTime,
  lastModifiedAt: DateTime,
  defaultBranch: Identifier,
  properties: PropertyList.T,
  links: HATEOAS.T,
  branches: Seq[BranchSummary]
)

case class ProjectList(
  projects: Seq[ProjectSummary],
  links: HATEOAS.T
)
