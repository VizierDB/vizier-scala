package info.vizierdb.ui.components.dataset

import info.vizierdb.nativeTypes.CellDataType
import org.scalajs.dom
import scalatags.JsDom.all._

trait TableDataSource
{
  def rowCount: Long
  def columnCount: Int
  
  def columnTitle(column: Int): String
  def columnDataType(column: Int): CellDataType
  def columnWidthInPixels(column: Int): Int

  def cellAt(row: Long, column: Int): Frag
  def rowAt(row: Long): Seq[Frag] = 
    (0 until columnCount).map { cellAt(row, _) }
  
  def headerAt(column: Int): Frag
  def headerCells: Seq[Frag] =
    (0 until columnCount).map { headerAt(_) }

  def rowClasses(row: Long): Seq[String]
}
