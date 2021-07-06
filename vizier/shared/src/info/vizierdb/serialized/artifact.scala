package info.vizierdb.serialized

import info.vizierdb.types.{ Identifier, ArtifactType }
import info.vizierdb.shared.HATEOAS

sealed trait ArtifactSummary
{
  val key: Identifier
  val id: Identifier
  val objType: String
  val category: ArtifactType.T
  val name: String
  val links: HATEOAS.T
}

sealed trait ArtifactDescription extends ArtifactSummary

case class StandardArtifact(
  key: Identifier,
  id: Identifier,
  objType: String,
  category: ArtifactType.T,
  name: String,
  links: HATEOAS.T
) extends ArtifactDescription
{
  def toDatasetSummary(
    columns: Seq[DatasetColumn]
  ) =
    DatasetSummary(
      key = key,
      id = id,
      objType = objType,
      category = category,
      name = name,
      links = links,
      columns = columns
    )

  def toDatasetDescription(
    columns: Seq[DatasetColumn],
    rows: Seq[DatasetRow],
    rowCount: Long,
    offset: Long,
    properties: PropertyList.T,
    extraLinks: HATEOAS.T
  ) = 
    DatasetDescription(
      key = key,
      id = id,
      objType = objType,
      category = category,
      name = name,
      links = links ++ extraLinks,
      columns = columns,
      rows = rows,
      rowCount = rowCount,
      offset = offset,
      properties = properties
    )
}

case class DatasetSummary(
  key: Identifier,
  id: Identifier,
  objType: String,
  category: ArtifactType.T,
  name: String,
  links: HATEOAS.T,
  columns: Seq[DatasetColumn]
) extends ArtifactSummary

case class DatasetDescription(
  key: Identifier,
  id: Identifier,
  objType: String,
  category: ArtifactType.T,
  name: String,
  links: HATEOAS.T,
  columns: Seq[DatasetColumn],
  rows: Seq[DatasetRow],
  rowCount: Long,
  offset: Long,
  properties: PropertyList.T
) extends ArtifactDescription
