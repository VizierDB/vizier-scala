package info.vizierdb.serialized

import info.vizierdb.types.{ Identifier, ArtifactType }
import info.vizierdb.nativeTypes.{ CellDataType, JsValue }
import play.api.libs.json.JsString

sealed trait ArtifactSummary
{
  val key: Identifier
  val id: Identifier
  val objType: String
  val category: ArtifactType.T
  val name: String
  def t = category
}

sealed trait ArtifactDescription extends ArtifactSummary

case class StandardArtifact(
  key: Identifier,
  id: Identifier,
  projectId: Identifier,
  objType: String,
  category: ArtifactType.T,
  name: String,
) extends ArtifactDescription
{
  def toDatasetSummary(
    columns: Seq[DatasetColumn]
  ) =
    DatasetSummary(
      key = key,
      id = id,
      projectId = projectId,
      objType = objType,
      category = category,
      name = name,
      columns = columns
    )

  def toDatasetDescription(
    columns: Seq[DatasetColumn],
    rows: Seq[DatasetRow],
    rowCount: Long,
    offset: Long,
    properties: PropertyList.T,
  ) = 
    DatasetDescription(
      key = key,
      id = id,
      projectId = projectId,
      objType = objType,
      category = category,
      name = name,
      columns = columns,
      rows = rows,
      rowCount = rowCount,
      offset = offset,
      properties = properties
    )

  def addPayload(
    payload: JsValue
  ) =
    JsonArtifactDescription(
      key = key,
      id = id,
      projectId = projectId,
      objType = objType,
      category = category,
      name = name,
      payload = payload
    )
  
  def addPayload(
    payload: String
  ) =
    JsonArtifactDescription(
      key = key,
      id = id,
      projectId = projectId,
      objType = objType,
      category = category,
      name = name,
      payload = JsString(payload)
    )
}

case class JsonArtifactDescription(
  key: Identifier,
  id: Identifier,
  projectId: Identifier,
  objType: String,
  name: String,
  category: ArtifactType.T,
  payload: JsValue
) extends ArtifactDescription

case class DatasetSummary(
  key: Identifier,
  id: Identifier,
  projectId: Identifier,
  objType: String,
  category: ArtifactType.T,
  name: String,
  columns: Seq[DatasetColumn]
) extends ArtifactSummary

case class DatasetDescription(
  key: Identifier,
  id: Identifier,
  projectId: Identifier,
  objType: String,
  category: ArtifactType.T,
  name: String,
  columns: Seq[DatasetColumn],
  rows: Seq[DatasetRow],
  rowCount: Long,
  offset: Long,
  properties: PropertyList.T
) extends ArtifactDescription
