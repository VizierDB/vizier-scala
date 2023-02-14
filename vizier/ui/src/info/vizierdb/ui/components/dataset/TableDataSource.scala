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

  def cellAt(row: Long, column: Int, width: Int, position: Int): Frag
  
  def rowClasses(row: Long): Seq[String]

  def rowCaveat(row: Long): Option[() => Unit]
}
