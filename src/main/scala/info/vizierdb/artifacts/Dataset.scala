/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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
package info.vizierdb.artifacts

import play.api.libs.json._
import info.vizierdb.types.Identifier
import org.apache.spark.sql.types.StructField
import org.mimirdb.spark.Schema

case class DatasetColumn(
  id: Identifier,
  name: String,
  `type`: String
)
{
  def toSpark: StructField =
    StructField(name, Schema.decodeType(`type`))
}
object DatasetColumn
{
  implicit val format: Format[DatasetColumn] = Json.format
}

case class DatasetRow(
  id: Identifier,
  values: Seq[JsValue]
)
object DatasetRow
{
  implicit val format: Format[DatasetRow] = Json.format
}

case class DatasetAnnotation(
  columnId: Identifier,
  rowId: Identifier,
  key: String,
  value: String
)
object DatasetAnnotation
{
  implicit val format: Format[DatasetAnnotation] = Json.format
}

case class Dataset(
  columns: Seq[DatasetColumn],
  rows: Seq[DatasetRow],
  annotations: Option[Seq[DatasetAnnotation]]
)
object Dataset
{
  implicit val format: Format[Dataset] = Json.format
}

