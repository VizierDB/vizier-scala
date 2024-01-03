package info.vizierdb.ui.roots

import org.scalajs.dom.document
import rx._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.util.Logging
import org.scalajs.dom
import info.vizierdb.ui.components.ProjectListView

object ProjectList
  extends Logging
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectList = new ProjectListView()
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild( projectList.root )
      OnMount.trigger(document.body)
    })
  }
}