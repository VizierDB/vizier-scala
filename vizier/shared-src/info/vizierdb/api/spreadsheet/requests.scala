package info.vizierdb.api.spreadsheet

import info.vizierdb.types._
import play.api.libs.json._
import info.vizierdb.nativeTypes.CellDataType
import info.vizierdb.serialized
import info.vizierdb.serializers._

sealed trait SpreadsheetRequest
object SpreadsheetRequest
{
  implicit val format: Format[SpreadsheetRequest] = Json.format
}

case class Ping(id: Int) extends SpreadsheetRequest
object Ping
{
  implicit val format: Format[Ping] = Json.format
}

case class OpenSpreadsheet(projectId: Identifier, datasetId: Identifier) extends SpreadsheetRequest
object OpenSpreadsheet
{
  implicit val format: Format[OpenSpreadsheet] = Json.format
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

