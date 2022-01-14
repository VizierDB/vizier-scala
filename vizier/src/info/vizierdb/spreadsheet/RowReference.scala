package info.vizierdb.spreadsheet

import info.vizierdb.types._

sealed trait RowReference

case class RowByIndex(idx: Long) extends RowReference
case class InsertedRow(insertId: Identifier, index: Int) extends RowReference