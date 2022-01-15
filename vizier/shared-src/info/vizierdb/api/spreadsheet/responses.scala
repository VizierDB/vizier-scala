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
    ReportError(e.getMessage(), e.getStackTrace.map { _.toString() }.mkString("\n"))
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

