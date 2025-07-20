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
package info.vizierdb.api.spreadsheet

import info.vizierdb.types._
import play.api.libs.json._
import info.vizierdb.nativeTypes.CellDataType
import info.vizierdb.serialized
import info.vizierdb.serializers._

sealed trait SpreadsheetRequest

case class Ping(id: Int) extends SpreadsheetRequest
object Ping
{
  implicit val format: Format[Ping] = Json.format
}

case class OpenEmpty() extends SpreadsheetRequest
object OpenEmpty
{
  implicit val format = Format[OpenEmpty](
    new Reads[OpenEmpty] {
      def reads(json: JsValue): JsResult[OpenEmpty] = 
        JsSuccess(OpenEmpty())
    },
    new Writes[OpenEmpty] {
      def writes(o: OpenEmpty): JsValue = 
        Json.obj()
    }
  )
}

sealed trait DatasetInitializer extends SpreadsheetRequest
object DatasetInitializer
{
  implicit val format: Format[DatasetInitializer] = Json.format
}

case class OpenDataset(projectId: Identifier, datasetId: Identifier) extends DatasetInitializer
object OpenDataset
{
  implicit val format: Format[OpenDataset] = Json.format
}

case class OpenWorkflowCell(projectId: Identifier, branchId: Identifier, moduleId: Identifier) extends DatasetInitializer
object OpenWorkflowCell
{
  implicit val format: Format[OpenWorkflowCell] = Json.format
}

case class SaveWorkflowCell(projectId: Identifier, branchId: Identifier, moduleId: Identifier, input: Option[String], output: Option[String]) extends SpreadsheetRequest
object SaveWorkflowCell
{
  implicit val format: Format[SaveWorkflowCell] = Json.format
}

case class SubscribeRows(row: Long, count: Int) extends SpreadsheetRequest
object SubscribeRows
{
  implicit val format: Format[SubscribeRows] = Json.format
}

case class UnsubscribeRows(row: Long, count: Int) extends SpreadsheetRequest
object UnsubscribeRows
{
  implicit val format: Format[UnsubscribeRows] = Json.format
}

case class EditCell(column: Int, row: Long, value: JsValue) extends SpreadsheetRequest
object EditCell
{
  implicit val format: Format[EditCell] = Json.format
}

object SpreadsheetRequest
{
  implicit val format: Format[SpreadsheetRequest] = Json.format
}