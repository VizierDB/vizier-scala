package info.vizierdb.spreadsheet

case class SerializedSpreadsheetUpdate(
  column: ColumnRef,
  from: Long,
  to: Long,
  frame: ReferenceFrame,
  expression: String
)

case class SerializedSpreadsheet(
  frame: ReferenceFrame,
  data: Seq[SerializedSpreadsheetUpdate]
)