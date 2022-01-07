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
  rowDimensions: (Int, Int),
  outerDimensions: (String, String),
  headerHeight: Int,
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
   * The width of a single row in pixels
   */
  def rowWidth = rowDimensions._1

  /**
   * The height of a single row in pixels
   */
  def rowHeight = rowDimensions._2

  ///// Update Logic

  /**
   * React to the table scroll moving down to the specified 
   * position / height by injecting and/or removing.
   * @param   position    The position, in pixels, of the top of the scroll view
   * @param   height      The position, in pixels, of the bottom of the scroll view
   */
  def updateScroller(position: Double = root.scrollTop, height: Double = root.offsetHeight): Unit =
  {
    val minRow = math.max(0, ((position) / rowHeight).toLong - 2)
    val maxRow = math.min(data.rowCount, ((position + height - headerHeight) / rowHeight).toLong + 3)

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

  def setData(data: TableDataSource, invalidate: Boolean = true) =
    ???

  ///// DOM structures

  class Row(row: Long)
  {
    val root: dom.html.TableRow = 
      tr(
        `class` := (Seq(
          (if(row % 2 == 0) { "even_row" } else { "odd_row" }),
        ) ++ data.rowClasses(row)).mkString(" "),
        position := "absolute",
        top := s"${row * rowHeight + headerHeight}px",
        left := "0px",
        height := s"${rowHeight}px",
        width := s"${rowWidth}px",
        td(`class` := "gutter", (row+1).toString)
      ).render
    refresh()

    def refresh()
    {
      while(root.firstChild != root.lastChild) { root.removeChild(root.lastChild) }
      for(cell <- data.rowAt(row)){ root.appendChild(cell) }
    }

  }

  /**
   * The header row
   */
  val header = thead(
    position := "absolute",
    zIndex := "10",
    backgroundColor := "#fff",
    width := s"${rowWidth}px",
    height := s"${headerHeight}px",

    tr()
  ).render
  rebuildHeaderRow()

  /**
   * The body component
   */
  val body = 
      tbody(
        width := "100%",
        height := s"${rowHeight * data.rowCount + headerHeight}px",
        position := "relative",
      ).render

  /**
   * The actual structure
   */
  val root = 
    div(
      `class` := "data_table",
      width := outerDimensions._1,
      height := outerDimensions._2,
      onscroll := { _:dom.Node => requestScrollUpdate() },
      overflow := "auto",
      table(
        header,
        body
      )
    ).render
  updateScroller()
  /**
   * Rebuild the header row, e.g., in response to added columns
   */
  def rebuildHeaderRow()
  {
    header.replaceChild(
      tr(
        ( Seq[Frag](
            td(`class` := "gutter", ""),
          )++data.headerCells
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