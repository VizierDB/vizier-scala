package info.vizierdb.spreadsheet

import play.api.libs.json._

case class SerializedSpreadsheetUpdate(
  column: ColumnRef,
  from: Long,
  to: Long,
  frame: ReferenceFrame,
  expression: String
)
object SerializedSpreadsheetUpdate
{
  implicit val format: Format[SerializedSpreadsheetUpdate] = Json.format
}

case class SerializedSpreadsheet(
  frame: ReferenceFrame,
  data: Seq[SerializedSpreadsheetUpdate]
)
object SerializedSpreadsheet
{
  implicit val format: Format[SerializedSpreadsheet] = Json.format
}
