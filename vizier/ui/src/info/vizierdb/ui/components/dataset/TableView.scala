package info.vizierdb.ui.components.dataset

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import scala.collection.mutable
import info.vizierdb.util.{ Logging, ArrayDeque }
import info.vizierdb.nativeTypes.CellDataType
import play.api.libs.json.JsString

class TableView(
  var data: TableDataSource,
  rowHeight: Int,
  maxHeight: Int,
  headerHeight: Int,
  maxWidth: Int = 800,
)(implicit val owner: Ctx.Owner)
  extends Logging
{
  ///// Variables

  /**
   * Index of the first row with a visible dom node
   */
  var firstRowIndex = 0l

  /**
   * 1+the index of the last row with a visible dom node
   */
  def lastRowIndex = firstRowIndex + visibleRows.size

  /**
   * References to all of the visible rows
   */
  var visibleRows = new ArrayDeque[Row]()

  /**
   * The height of the inner (full table) view
   */
  def innerHeight = rowHeight * data.rowCount
  
  /**
   * The height of the outer (scrolling wrapper) view
   */
  def outerHeight = Math.min(innerHeight, maxHeight)

  /**
   * The width of the inner (full table) view
   */
  def innerWidth = data.columnCount * TableView.DEFAULT_CELL_WIDTH + TableView.GUTTER_WIDTH

  /**
   * The width of the outer (wrapper) view
   */

  val PREFIX_ROWS = 10
  val SUFFIX_ROWS = 10

  ///// Update Logic

  /**
   * React to the table scroll moving down to the specified 
   * position / height by injecting and/or removing.
   * @param   position    The position, in pixels, of the top of the scroll view
   * @param   height      The position, in pixels, of the bottom of the scroll view
   */
  def updateScroller(position: Double = root.scrollTop, height: Double = root.clientHeight): Unit =
  {
    val minRow = math.max(0, ((position) / rowHeight).toLong - PREFIX_ROWS)
    val maxRow = math.min(data.rowCount, ((position + height) / rowHeight).toLong + SUFFIX_ROWS)

    logger.trace(s"Range ${root.scrollTop} to ${root.scrollTop + root.offsetHeight}")
    logger.trace(s"With rows from $minRow to $maxRow @ $firstRowIndex")

    while(!visibleRows.isEmpty && firstRowIndex < minRow){
      logger.trace(s"Dropping row $firstRowIndex from front (${visibleRows.size} children)")
      body.removeChild(visibleRows.removeFirst.root)
      firstRowIndex += 1
    }
    while(firstRowIndex > minRow){
      firstRowIndex -= 1
      logger.trace(s"Injecting row $firstRowIndex at front")
      val row = new Row(firstRowIndex)
      visibleRows.prepend(row)
      if(body.hasChildNodes()){
        body.insertBefore(row.root, body.firstChild)
      } else {
        body.appendChild(row.root)
      }
    }
    firstRowIndex = minRow
    while(!visibleRows.isEmpty && lastRowIndex > maxRow)
    {
      logger.trace(s"Dropping row ${lastRowIndex} from end")
      body.removeChild(visibleRows.removeLast.root)
    }
    while(lastRowIndex < maxRow)
    {
      logger.trace(s"Injecting row ${lastRowIndex} at end")
      val row = new Row(lastRowIndex)
      visibleRows.append(row)
      body.appendChild(row.root)
    }
  }

  def refresh(offset: Long, limit: Int) =
  {
    val refreshLowerBound = math.max(offset - firstRowIndex, 0)
    val refreshUpperBound = math.min(offset + limit - firstRowIndex, visibleRows.size)

    if(refreshLowerBound < refreshUpperBound){ 
      for(i <- refreshLowerBound until refreshUpperBound){
        visibleRows(i.toInt).refresh()
      }
    }
    updateScroller()
  }

  def refreshCell(row: Long, column: Int) =
  {
    refresh(row, 1)
  }

  def refreshSize() =
  {
    body.style.height = s"${innerHeight}px"
  }


  def setData(data: TableDataSource, invalidate: Boolean = true) =
  {
    this.data = data
    if(invalidate) { 
      rebuildHeaderRow()
      refreshSize()
      refresh(firstRowIndex, visibleRows.size)
    }
  }

  ///// DOM structures

  class Row(row: Long)
  {
    val root: dom.html.Div = 
      div(
        `class` := (Seq(
          (if(row % 2 == 0) { "even_row" } else { "odd_row" }),
          "table_row",
        ) ++ data.rowClasses(row)).mkString(" "),
        position := "absolute",
        top := s"${row * rowHeight}px",
        left := "0px",
        height := s"${rowHeight}px",
        width := "100%",
        RenderCell.gutter(
          row = row, 
          caveatted = data.rowCaveat(row),
          width = TableView.GUTTER_WIDTH
        )
      ).render
    refresh()

    def refresh()
    {
      while(root.firstChild != root.lastChild) { root.removeChild(root.lastChild) }
      for(i <- 0 until data.columnCount){ 
        root.appendChild(data.cellAt(row, i, TableView.DEFAULT_CELL_WIDTH))
      }
    }

  }

  /**
   * The body component
   */
  val body = 
      div(
        `class` := "table_body",
        height := s"${innerHeight}px",
        width := "100%"
      ).render

  /**
   * The header row
   */
  val header = div(
    `class` := "table_header",
    height := s"${headerHeight}px",
    width := "100%",
    div()
  ).render

  val tableContents =
    div(`class` := "table_contents",
      header,
      body
    ).render
  rebuildHeaderRow()

  /**
   * The actual structure
   */
  val root = 
    div(
      OnMount { _ => updateScroller() },
      `class` := "data_table",
      onscroll := { _:dom.Node => requestScrollUpdate() },
      tableContents
    ).render
  /**
   * Rebuild the header row, e.g., in response to added columns
   */
  def rebuildHeaderRow()
  {
    tableContents.style.width = s"${innerWidth+20}px"
    header.replaceChild(
      div(
        ( Seq[Frag](
            div(
              `class` := "gutter", 
              width := TableView.GUTTER_WIDTH,
              ""
            ),
          )++(0 until data.columnCount).map { i =>
            RenderCell.header(
              data.columnTitle(i), 
              data.columnDataType(i),
              width = TableView.DEFAULT_CELL_WIDTH
            )
          }
        ):_*
      ).render,
      header.firstChild
    )
  }

  ////// Scrollbar Magic

  // See MDN guide for using the scroll event
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/scroll_event
  var ticking = false

  def requestScrollUpdate(): Unit =
  {
    if(ticking){ return; }
    ticking = true
    dom.window.requestAnimationFrame( (_) => { 
      updateScroller()
      ticking = false
    } )
  }

}

object TableView
{
  /**
   * The width of a cell (TODO: allow users to override this by dragging cols)
   */
  val DEFAULT_CELL_WIDTH = 150
  val GUTTER_WIDTH = 20
}