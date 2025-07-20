/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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
