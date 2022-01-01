package info.vizierdb.serialized

import info.vizierdb.shared.HATEOAS
import info.vizierdb.types.Identifier
import info.vizierdb.nativeTypes.DateTime
import info.vizierdb.nativeTypes

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

  def apply(key: String): Option[nativeTypes.JsValue] = 
    properties.find { _.key == key }
              .map { _.value }
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
{
    def apply(key: String): Option[nativeTypes.JsValue] = 
    properties.find { _.key == key }
              .map { _.value }
}

case class ProjectList(
  projects: Seq[ProjectSummary],
  links: HATEOAS.T
)
