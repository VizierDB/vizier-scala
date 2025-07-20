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

sealed trait SpreadsheetResponse
object SpreadsheetResponse
{
  implicit val format: Format[SpreadsheetResponse] = Json.format
}

case class Connected(name: String) extends SpreadsheetResponse
object Connected
{
  implicit val format: Format[Connected] = Json.format
}

case class UpdateSize(rows: Long) extends SpreadsheetResponse
object UpdateSize
{
  implicit val format: Format[UpdateSize] = Json.format
}

case class UpdateSchema(schema: Seq[serialized.DatasetColumn]) extends SpreadsheetResponse
object UpdateSchema
{
  implicit val format: Format[UpdateSchema] = Json.format
}

case class Pong(id: Int) extends SpreadsheetResponse
object Pong
{
  implicit val format: Format[Pong] = Json.format
}

case class ReportError(message: String, detail: String) extends SpreadsheetResponse
object ReportError
{
  implicit val format: Format[ReportError] = Json.format
  def apply(e: Throwable): ReportError = 
    ReportError(
      e.getClass.getSimpleName + Option(e.getMessage()).map { ": " + _ }.getOrElse { "" }, 
      e.getStackTrace.map { _.toString() }.mkString("\n")
    )
}

case class DeliverRows(row: Long, data: Array[Array[SpreadsheetCell]]) extends SpreadsheetResponse
object DeliverRows
{
  implicit val format: Format[DeliverRows] = Json.format
}

case class DeliverCell(column: Int, row: Long, value: SpreadsheetCell) extends SpreadsheetResponse
object DeliverCell
{
  implicit val format: Format[DeliverCell] = Json.format
}

