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

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import scalajs.js
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._
import info.vizierdb.serialized.{ DatasetDescription, DatasetColumn, DatasetRow }
import info.vizierdb.nativeTypes.JsValue
import scala.concurrent.Future
import scala.concurrent.Promise
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.Vizier
import info.vizierdb.util.RowCache
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.network.SpreadsheetClient

/**
 * A representation of a dataset artifact
 * 
 * Dataset wraps around a set of structures that support the spreadsheet view.
 * - RowCache: A bounded-size cache to avoid having to load the entire dataset 
 *             into memory all at once (and to allow incremental loading).
 * - TableView: Handles the actual display of data.  Pages row nodes into and 
 *              out of the DOM to show only the nodes that are actually being
 *              displayed.
 * - StaticDataSource: Trivial wrapper around RowCache that handles displaying
 *                     individual cells.  This exists because we want to be able
 *                     to swap it out for an EditableDataSource if the user 
 *                     starts an editing session.
 * - EditableDataSource: (Not implemented yet; Will handle editing the data)
 * - RenderCell: Abstracts out the logic for rendering individual cell values.
 */
class Dataset(
  datasetId: Identifier,
  projectId: Identifier = Vizier.project.now.get.projectId,
  menu: Seq[Dataset.Command] = Dataset.DEFAULT_COMMANDS,
  onclick: (Long, Int) => Unit = { (_, _) => () }
)(implicit val owner: Ctx.Owner)
{
  val ROW_HEIGHT = 30

  implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  val cache = new RowCache[DatasetRow] (
                    fetchRowsWithAPI, 
                    (candidates, pageSize) => candidates.maxBy { pageIdx => math.abs(pageIdx - table.firstRowIndex) }
                  )
  var table: TableView = null

  def setSource(source: TableDataSource, invalidate: Boolean = true){
    if(table == null){
      table = new TableView(
        data = source,
        rowHeight = ROW_HEIGHT,
        maxHeight = 400,
        headerHeight = 40
      )
      root.appendChild(table.root)
      cache.onRefresh.append(table.refresh(_,_))
    } else { 
      source match {
        case c: SpreadsheetClient => c.table = Some(table)
        case _ => ()
      }
      table.setData(source, invalidate = invalidate)
    }
  }

  val name = Var[String]("unnamed")

  def this(description: DatasetDescription, projectId: Identifier, menu: Seq[Dataset.Command], onclick: (Long, Int) => Unit)
          (implicit owner: Ctx.Owner) =
  {
    this(description.id, projectId, menu, onclick)
    rebind(description)
  }

  // def this(description: DatasetDescription, projectId: Identifier) 
  //         (implicit owner: Ctx.Owner) =
  //   this(description, projectId, menu = Dataset.DEFAULT_COMMANDS, onclick = { (_:Long, _: Int) => () })

  // def this(description: DatasetDescription, menu: Seq[Dataset.Command], onclick: (Long, Int) => Unit)
  //         (implicit owner: Ctx.Owner) =
  //   this(description, description.projectId, menu, onclick)

  // def this(description: DatasetDescription, menu: Seq[Dataset.Command])
  //         (implicit owner: Ctx.Owner) =
  //   this(description, description.projectId, menu, onclick = { (_:Long, _:Int) => () })

  def this(description: DatasetDescription)
          (implicit owner: Ctx.Owner) =
    this(
      description, 
      description.projectId, 
      menu = Dataset.DEFAULT_COMMANDS,
      onclick = { (_:Long, _: Int) => () }
    )

  def rebind(description: DatasetDescription)
  {
    cache.clear()
    setSource(new StaticDataSource(cache, description, onclick = onclick))
    name() = description.name
  }

  def fetchRowsWithAPI(offset: Long, limit: Int): Future[Seq[DatasetRow]] = 
  {
    println(s"Fetch Dataset Rows @ $offset -> ${offset+limit}")
    Vizier.api.artifactGetDataset(
      artifactId = datasetId,
      projectId = projectId,
      offset = Some(offset),
      limit = Some(limit)
    ).map { _.rows }
  }

  val root:dom.html.Div = div(
    `class` := "dataset",
    div(
      `class` := "header",
      Rx { 
        h3(if(name().isEmpty()) { "Untitled Dataset "} else { name() })
      }.reactive,
      Rx { 
        span(menu.map { _(projectId, datasetId, name()) })
      }.reactive
    )
    // Table root is appended by setSource()
  ).render
}

object Dataset
{
  type Command = (Identifier, Identifier, String) => Frag
  val COMMAND_OPEN_SPREADSHEET = 
    (projectId: Identifier, datasetId: Identifier, datasetName: String) =>
      a(
        href := Vizier.links.spreadsheet(projectId, datasetId),
        target := "_blank",
        FontAwesome("table")
      )

  val COMMAND_DOWNLOAD =
    (projectId: Identifier, datasetId: Identifier, datasetName: String) =>
      a(
        href := Vizier.api.artifactGetCsvURL(projectId, datasetId, name = Some(datasetName)),
        target := "_blank",
        FontAwesome("download")
      )

  val DEFAULT_COMMANDS = Seq(COMMAND_DOWNLOAD)
}