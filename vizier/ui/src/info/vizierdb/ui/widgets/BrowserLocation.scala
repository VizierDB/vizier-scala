package info.vizierdb.ui.widgets

import org.scalajs.dom
import scalajs.js.URIUtils.encodeURIComponent

object BrowserLocation
{
  def queryString(terms: Seq[(String, String)]): String =
    if(terms.isEmpty){ "" }
    else {
      "?" + terms.map { case (a, b) => 
        encodeURIComponent(a) + "=" + encodeURIComponent(b)
      }.mkString("&")
    }

  def replaceQuery(terms: (String, String)*): Unit =
  {
    if(terms.isEmpty){
      dom.window.history.replaceState(null, "", "?")
    } else {
      dom.window.history.replaceState(null, "", queryString(terms))
    }
  }
}