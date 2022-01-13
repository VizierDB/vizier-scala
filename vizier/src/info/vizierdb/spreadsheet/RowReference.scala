package info.vizierdb.spreadsheet

import info.vizierdb.types._

sealed trait RowReference

case class SourceRowByIndex(idx: Long) extends RowReference
case class InsertedRow(insertId: Identifier, index: Int) extends RowReference