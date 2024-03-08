package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalatags.JsDom.all._

object SideMenu {

  private var isOpen = false
  val sideMenuElement =
    div(`class` := "sidemenu", style := "visibility: hidden").render

  def toggleMenu(buttonEvent: dom.MouseEvent)(content: dom.Node*): Unit = {
    if (isOpen) {
      hide()
      isOpen = false
    } else {
      showAt(buttonEvent.pageX + 10, buttonEvent.pageY)
      isOpen = true
    }
  }

  def showAt(x: Double, y: Double): Unit = {
    sideMenuElement.style.left = s"${x}px"
    sideMenuElement.style.top = s"${y}px"
    sideMenuElement.style.visibility = "visible"
    sideMenuElement.style.opacity = "1.0"
  }

  def hide(): Unit = {
    sideMenuElement.style.visibility = "hidden"
    sideMenuElement.style.opacity = "0"
  }
}
