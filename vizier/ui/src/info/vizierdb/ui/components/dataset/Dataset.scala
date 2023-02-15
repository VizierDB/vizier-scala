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
import info.vizierdb.ui.components.Module

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
  editingDetails: DatasetEditingDetails = DatasetNotEditable
)(implicit val owner: Ctx.Owner)
{
  val ROW_HEIGHT = 30

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val cache = new RowCache[DatasetRow] (
                    fetchRowsWithAPI, 
                    (candidates, pageSize) => candidates.maxBy { pageIdx => math.abs(pageIdx - table.firstRowIndex) }
                  )
  var table: TableView = null

  def setSource(source: TableDataSource){
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
      table.setData(source)
    }
  }

  val name = Var[String]("unnamed")

  def this(description: DatasetDescription, projectId: Identifier, module: Option[Module])
          (implicit owner: Ctx.Owner) =
  {
    this(description.id, projectId, 
      module match {
        case Some(module) => 
          module.subscription.branch match {
            case Left(branch) => // an active branch
              DatasetEditsAfterModule(branch.branchId, module.realModuleId.get)
            case Right(_) => // a static workflow
              DatasetNotEditable
          }
        case None =>
          DatasetNotEditable
      }
    )
    setSource(new StaticDataSource(cache, description))
    name() = description.name
  }

  def this(description: DatasetDescription, module: Option[Module])
          (implicit owner: Ctx.Owner) =
    this(description, description.projectId, module)

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
      a(
        href := editingDetails.url(projectId, datasetId),
        target := "_blank",
        FontAwesome("table")
      ),
      a(
        href := Vizier.api.artifactGetCsvURL(projectId, datasetId),
        target := "_blank",
        FontAwesome("download")
      )
    )
    // Table root is appended by setSource()
  ).render

}