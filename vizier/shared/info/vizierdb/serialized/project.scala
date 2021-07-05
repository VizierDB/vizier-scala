package info.vizierdb.serialized

import java.time.ZonedDateTime
import info.vizierdb.shared.HATEOAS
import info.vizierdb.types.Identifier

case class ProjectSummary(
  id: Identifier,
  createdAt: ZonedDateTime,
  lastModifiedAt: ZonedDateTime,
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
  createdAt: ZonedDateTime,
  lastModifiedAt: ZonedDateTime,
  defaultBranch: Identifier,
  properties: PropertyList.T,
  links: HATEOAS.T,
  branches: Seq[BranchSummary]
)

case class ProjectList(
  projects: Seq[ProjectSummary],
  links: HATEOAS.T
)
