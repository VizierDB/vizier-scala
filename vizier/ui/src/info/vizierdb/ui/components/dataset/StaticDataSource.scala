package info.vizierdb.ui.components.dataset

import info.vizierdb.serialized.{
  DatasetRow,
  DatasetColumn
}
import info.vizierdb.nativeTypes.CellDataType
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized.DatasetDescription

class StaticDataSource(
  val rowCount: Long,
  cache: RowCache[DatasetRow], 
  schema: Seq[DatasetColumn]
)
  extends TableDataSource
{

  def this(cache: RowCache[DatasetRow], description: DatasetDescription)
  {
    this(
      description.rowCount,
      cache,
      description.columns
    )
    cache.preload(description.rows)
  }

  override def columnCount: Int = 
    schema.size

  override def columnTitle(column: Int): String = 
    schema(column).name

  override def columnDataType(column: Int): CellDataType = 
    schema(column).`type`

  override def columnWidthInPixels(column: Int): Int = 
    RenderCell.defaultWidthForType(columnDataType(column))

  override def cellAt(row: Long, column: Int): Frag = 
  {
    cache(row) match {
      case None => 
        td(
          textAlign := "center", 
          width := RenderCell.defaultWidthForType(columnDataType(column)),
          Spinner(15)
        ).render
      case Some(DatasetRow(id, values, cellCaveats, _)) => 
        RenderCell(
          values(column), 
          columnDataType(column),
          caveatted = cellCaveats.map { _(column) }.getOrElse { false }
        )
    }
  }

  def headerAt(column: Int): Frag =
    RenderCell.header(columnTitle(column), columnDataType(column))

  override def rowClasses(row: Long): Seq[String] =
    if(cache(row).flatMap { _.rowIsAnnotated }.getOrElse(false)){ 
      Seq("caveatted")
    } else { Seq.empty }



}