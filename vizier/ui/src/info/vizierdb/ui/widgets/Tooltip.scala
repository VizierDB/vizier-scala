package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._

object Tooltip
{
  lazy val tooltip = {
    val tt = div(`class` := "tooltip", style := "visibility: hidden;", "???").render
    dom.window.document.body.appendChild(tt)
    /* return */ tt
  }
  private var triggerHandle = -1

  def show(x: Double, y: Double)(content: dom.Node*): Unit =
  {
    tooltip.style.left = s"${x}px"
    tooltip.style.top  = s"${y}px"
    while(tooltip.firstChild != null){
      tooltip.removeChild(tooltip.firstChild)
    }
    content.foreach { tooltip.appendChild(_) }
    clearTrigger()
    tooltip.style.visibility = "visible"
    tooltip.style.opacity = "1.0"
  }

  def showSoon(x: Double, y: Double)(content: dom.Node*): Unit =
  {
    clearTrigger()
    triggerHandle = dom.window.setTimeout({ () => 
      show(x, y)(content:_*)
    }, 1000)
  }

  def showSoon(evt: dom.MouseEvent)(content: dom.Node*): Unit =
  {
    showSoon(evt.pageX + 20, evt.pageY + 20)(content:_*)
  }

  def hide(): Unit =
  {
    tooltip.style.visibility = "hidden"
    tooltip.style.opacity = "0"
    // while(tooltip.firstChild != null){
    //   tooltip.removeChild(tooltip.firstChild)
    // }
  }

  def hideSoon(): Unit =
  {
    clearTrigger()
    triggerHandle = dom.window.setTimeout({ () => 
      hide()
    }, 100)
  }

  def clearTrigger(): Unit =
  {
    if(triggerHandle >= 0){
      dom.window.clearTimeout(triggerHandle)
      triggerHandle = -1
    }
  }

  def apply(msg: String): Seq[AttrPair] = 
    apply(span(msg))

  def apply(elem: Frag*): Seq[AttrPair] =
    make(elem.map { _.render }:_*)

  def make(content: dom.Node*): Seq[AttrPair] = 
    Seq(
      onmouseover := { (evt:dom.MouseEvent) => 
        showSoon(evt)(content:_*)
      },
      onmouseout := { (_:dom.Event) => 
        hideSoon()
      }
    )
}