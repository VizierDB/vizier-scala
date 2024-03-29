/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.ui.components.dataset

import info.vizierdb.serialized.{
  DatasetRow,
  DatasetColumn
}
import info.vizierdb.nativeTypes.CellDataType
import info.vizierdb.types._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized.DatasetDescription
import info.vizierdb.util.RowCache

class StaticDataSource(
  val rowCount: Long,
  cache: RowCache[DatasetRow], 
  schema: Seq[DatasetColumn],
  projectId: Identifier,
  datasetId: Identifier,
  onclick: (Long, Int) => Unit = { (_, _) => () }
)
  extends TableDataSource
{

  def this(cache: RowCache[DatasetRow], description: DatasetDescription, onclick: (Long, Int) => Unit)
  {
    this(
      description.rowCount,
      cache,
      description.columns,
      projectId = description.projectId,
      datasetId = description.id,
      onclick = onclick
    )
    cache.preload(description.rows),
  }
  def this(cache: RowCache[DatasetRow], description: DatasetDescription)
  {
    this(cache, description, onclick = { (_, _) => () })
  }

  def displayCaveat(row: String, column: Option[Int])
  {
    CaveatModal(
      projectId = projectId,
      datasetId = datasetId,
      row = Some(row), 
      column = column
    ).show
  }

  override def columnCount: Int = 
    schema.size

  override def columnTitle(column: Int): String = 
    schema(column).name

  override def columnDataType(column: Int): CellDataType = 
    schema(column).`type`

  def columnWidthInPixels(column: Int): Int =
    TableView.DEFAULT_CELL_WIDTH

  override def cellAt(row: Long, column: Int, width: Int, xpos: Int): Frag = 
  {
    cache(row) match {
      case None => 
        RenderCell.spinner(columnDataType(column))
      case Some(DatasetRow(rowId, values, cellCaveats, _)) => 
        RenderCell(
          values(column), 
          columnDataType(column),
          width = width,
          position = xpos,
          caveatted = 
            if(cellCaveats.map { _(column) }.getOrElse { false }){
              Some( (trigger: dom.html.Button) => displayCaveat(rowId, Some(column)) )
            } else { None },
          onclick = { _ => onclick(row, column) }
        )
    }
  }

  override def rowClasses(row: Long): Seq[String] =
    if(cache(row).flatMap { _.rowIsAnnotated }.getOrElse(false)){ 
      Seq("caveatted")
    } else { Seq.empty }

  override def rowCaveat(row: Long): Option[() => Unit] =
    cache(row) match {
      case None => None
      case Some(data) => 
        if(data.rowIsAnnotated.getOrElse { false }){
          Some( () => displayCaveat(data.id, None) )
        } else { None }
    }

}