package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import scala.collection.mutable
import info.vizierdb.util.ArrayDeque

class TableView(
  var numRows: Int,
  rowDimensions: (Int, Int),
  outerDimensions: (Int, Int),
  getRow: Long => dom.html.Div
)
{

  var firstRowIndex = 0l

  def allocateRow(idx: Long): dom.html.Div = 
    div(
      position := "absolute",
      top := s"${idx * rowHeight}px",
      left := "0px",
      height := s"${rowHeight}px",
      width := s"${rowWidth}px",
      getRow(idx)
    ).render

  def rowWidth = rowDimensions._1
  def rowHeight = rowDimensions._2

  def updateScroller(position: Double, height: Double): Unit =
  {
    val minRow = math.max(0, (position / rowHeight).toLong - 2)
    val maxRow = math.min(numRows, ((position + height) / rowHeight).toLong + 3)

    println(s"${scroller.scrollTop} to ${scroller.scrollTop + scroller.offsetHeight}")
    println(s"With rows from $minRow to $maxRow @ $firstRowIndex")

    while(innerView.hasChildNodes() && firstRowIndex < minRow){
      println(s"Dropping row $firstRowIndex from front (${innerView.childElementCount} children)")
      innerView.removeChild(innerView.firstChild)
      firstRowIndex += 1
    }
    while(firstRowIndex > minRow){
      firstRowIndex -= 1
      println(s"Injecting row $firstRowIndex at front")
      if(innerView.hasChildNodes()){
        innerView.insertBefore(allocateRow((firstRowIndex)), innerView.firstChild)
      } else {
        innerView.appendChild(allocateRow((firstRowIndex)))
      }
    }
    firstRowIndex = minRow
    while(innerView.hasChildNodes() && firstRowIndex + innerView.childElementCount > maxRow)
    {
      println(s"Dropping row ${firstRowIndex + innerView.childElementCount} from end")
      innerView.removeChild(innerView.lastChild)
    }
    while(firstRowIndex + innerView.childElementCount < maxRow)
    {
      println(s"Injecting row ${firstRowIndex + innerView.childElementCount} at end")
      innerView.appendChild(allocateRow(firstRowIndex + innerView.childElementCount))
    }

    // val minPosition = minRow * rowHeight - position
    // println(s"Starting at $minPosition")

    // for(child <- 0 until innerView.childElementCount){
    //   val computedPosition = minPosition + child * rowHeight
    //   println(s"child $child (${child + firstRowIndex}) @ $computedPosition")
    //   innerView.children(child).asInstanceOf[dom.html.Div].style += s"top: $computedPosition"
    // }

  }

  // See MDN guide for using the scroll event
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/scroll_event
  var ticking = false

  def scrollTrigger(event: dom.Event): Unit =
  {
    if(ticking){ return; }
    dom.window.requestAnimationFrame( (_) => { 
      updateScroller(scroller.scrollTop, scroller.offsetHeight)
      ticking = false 
    } )
  }

  val innerView: dom.html.Div = 
    div(
      width := "100%",
      height := rowHeight * numRows,
      position := "relative"
    ).render

  val scroller:dom.html.Div = 
    div(
      OnMount { node => 
        updateScroller(scroller.scrollTop, 
                       scroller.offsetHeight) 
        node.addEventListener("scroll", scrollTrigger)
      },
      overflow := "auto",
      width := outerDimensions._1,
      height := outerDimensions._2,
      innerView
    ).render

  val root = div(overflow := "hidden", scroller)


}