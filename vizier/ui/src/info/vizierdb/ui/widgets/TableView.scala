package info.vizierdb.ui.widgets

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import scala.collection.mutable
import info.vizierdb.util.Logging

class TableView(
  var numRows: Long,
  rowDimensions: (Int, Int),
  outerDimensions: (Int, Int),
  headerHeight: Int,
  headers: Rx[Seq[dom.Node]],
  getRow: Long => dom.html.Div
)(implicit val owner: Ctx.Owner)
  extends Logging
{
  var firstRowIndex = 0l

  def setNumRows(numRows: Long) = 
  {
    this.numRows = numRows
    innerView.style.height = s"${rowHeight * numRows}px"
    requestScrollUpdate()
  }

  def allocateRow(idx: Long): dom.html.Div = 
  {
    val row = getRow(idx)
    row.classList.add("table_view_row")
    row.style.position = "absolute"
    row.style.top = s"${idx * rowHeight}px"
    row.style.left = "0px"
    row.style.height = s"${rowHeight}px"
    row.style.width = s"${rowWidth}px"
    row.style.backgroundColor = if(idx % 2 == 0) { "#fff" } else { "#ddd" }
    return row
  }

  def rowWidth = rowDimensions._1
  def rowHeight = rowDimensions._2

  def updateScroller(position: Double, height: Double): Unit =
  {
    val minRow = math.max(0, (position / rowHeight).toLong - 2)
    val maxRow = math.min(numRows, ((position + height) / rowHeight).toLong + 3)

    logger.trace(s"${scroller.scrollTop} to ${scroller.scrollTop + scroller.offsetHeight}")
    logger.trace(s"With rows from $minRow to $maxRow @ $firstRowIndex")

    while(innerView.hasChildNodes() && firstRowIndex < minRow){
      logger.trace(s"Dropping row $firstRowIndex from front (${innerView.childElementCount} children)")
      innerView.removeChild(innerView.firstChild)
      firstRowIndex += 1
    }
    while(firstRowIndex > minRow){
      firstRowIndex -= 1
      logger.trace(s"Injecting row $firstRowIndex at front")
      if(innerView.hasChildNodes()){
        innerView.insertBefore(allocateRow((firstRowIndex)), innerView.firstChild)
      } else {
        innerView.appendChild(allocateRow((firstRowIndex)))
      }
    }
    firstRowIndex = minRow
    while(innerView.hasChildNodes() && firstRowIndex + innerView.childElementCount > maxRow)
    {
      logger.trace(s"Dropping row ${firstRowIndex + innerView.childElementCount} from end")
      innerView.removeChild(innerView.lastChild)
    }
    while(firstRowIndex + innerView.childElementCount < maxRow)
    {
      logger.trace(s"Injecting row ${firstRowIndex + innerView.childElementCount} at end")
      innerView.appendChild(allocateRow(firstRowIndex + innerView.childElementCount))
    }

  }


  // See MDN guide for using the scroll event
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/scroll_event
  var ticking = false

  def requestScrollUpdate(): Unit =
  {
    if(ticking){ return; }
    ticking = true
    dom.window.requestAnimationFrame( (_) => { 
      updateScroller(scroller.scrollTop, scroller.offsetHeight)
      ticking = false
    } )
  }

  lazy val headerView = Rx { 
    thead(
      position := "sticky",
      top := "0px",
      left := "0px",
      width := "100%",
      height := s"${headerHeight}px",
      replaceNodeOnUpdate(r = headers, wrap = x => x)
    )
  }
  lazy val innerView: dom.html.TableSection = 
    tbody(
      `class` := "table_view_content",
      width := "100%",
      height := s"${rowHeight * numRows}px",
      position := "relative",
    ).render

  lazy val scroller:dom.html.Div = 
    div(
      OnMount { node => 
        updateScroller(scroller.scrollTop, 
                       scroller.offsetHeight) 
      },
      onscroll := { _:dom.Node => requestScrollUpdate() },
      `class` := "table_view_scroller",
      overflow := "auto",
      width := outerDimensions._1,
      height := outerDimensions._2,
      table(
        innerView
      )
    ).render

  lazy val root:dom.html.Div = div(
    `class` := "table_view",
    overflow := "hidden", 
    Rx { headerView },
    scroller
  ).render


}