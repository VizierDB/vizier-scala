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
import info.vizierdb.ui.widgets.TableView
import scala.concurrent.Promise
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.Vizier

class Dataset(
  datasetId: Identifier,
)(implicit val owner: Ctx.Owner)
{
  val ROW_HEIGHT = 30
  val CELL_WIDTH = 100
  val GUTTER_WIDTH = 40

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val columns = Var[Seq[Column]](Seq.empty)
  val size = Var[Long](0)
  val firstVisible = Var[Long](0)
  val rows = new RowCache[DatasetRow] (
                    fetchRowsWithAPI, 
                    _.maxBy { pageIdx => math.abs(pageIdx - firstVisible.now) }
                  )
  val name = Var[String]("unnamed")

  def this(description: DatasetDescription)
          (implicit owner: Ctx.Owner) =
  {
    this(description.id)
    loadColumns(description.columns)
    rows.preload(description.rows)
    name() = description.name
    size() = description.rowCount
    println(s"Dataset size: ${description.rowCount}")
  }

  def loadColumns(serializedColumns: Seq[DatasetColumn]) =
  {
    columns() = serializedColumns.map { new Column(_) }
  }

  def fetchRowsWithAPI(offset: Long, limit: Int): Future[Seq[DatasetRow]] = 
  {
    println(s"Fetch Dataset Rows @ $offset -> ${offset+limit}")
    Vizier.api.artifactGetDataset(
      artifactId = datasetId,
      projectId = Vizier.project.now.get.projectId,
      offset = Some(offset),
      limit = Some(limit)
    ).map { _.rows }
  }

  class Column(
    id: Identifier,
    name: String,
    dataType: JsValue
  ){
    val root = th(
      `class` := "table_column_header",
      name,
      width := s"${CELL_WIDTH}px",
      span(`class` := "column_type", s"($dataType)")
    )
    def this(encoded: DatasetColumn){
      this(encoded.id, encoded.name, encoded.`type`)
    }
  }

  def Gutter(index: Long) =
    td(`class` := "table_row_id", float := "left", width := s"${GUTTER_WIDTH}px", (index+1))

  def Cell(value: JsValue, isCaveatted: Boolean) = 
    td(
      `class` := "dataset_value "+
        (if(isCaveatted){ "caveatted" } else { "not_caveatted" }),
      width := s"${CELL_WIDTH}px",
      (
        if(value == null) { "" }
        else if(value.equals(js.undefined)){ "" }
        else { value.toString }
      )

    )
  def Row(encoded: DatasetRow, index: Long) = 
    tr(
      `class` := "table_row " + 
                 (if(encoded.rowIsAnnotated.getOrElse(false)){ "caveatted" } else { "" }),
      Gutter(index),
      encoded.values.zip(
        encoded.rowAnnotationFlags
               .getOrElse { encoded.values.map { _ => false }}
      ).map { case (value, isCaveatted) => Cell(value, isCaveatted) }
    )
  

  private val tableView = 
    new TableView(
      numRows = size.now.toInt, 
      rowDimensions = (780, ROW_HEIGHT),
      outerDimensions = (800, 400),
      headerHeight = 50,
      headers = Rx { th(width := s"${GUTTER_WIDTH}px").render +: columns().map { _.root.render }},
      getRow = { (position: Long) => 
        div(
          padding := "0px",
          margin := "0px",
          Rx { rows(position)() match {
            case None => tr(Gutter(position),
                            div(
                              width := "100%",
                              textAlign := "center", 
                              Spinner()
                          ))
            case Some(row) => Row(row,position)
          } }
        ).render
      }
    )
  size.triggerLater { tableView.setNumRows(_) }

  lazy val root = div(
    `class` := "dataset",
    Rx { h3(name()) },
    tableView.root
  )

}