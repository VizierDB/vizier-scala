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

case class OpenDataset(projectId: Identifier, datasetId: Identifier) extends SpreadsheetRequest
object OpenDataset
{
  implicit val format: Format[OpenDataset] = Json.format
}

case class OpenCell(projectId: Identifier, branchId: Identifier, moduleId: Identifier) extends SpreadsheetRequest
object OpenCell
{
  implicit val format: Format[OpenCell] = Json.format
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