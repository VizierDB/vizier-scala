package info.vizierdb.ui.components.settings

import rx._
import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.Vizier
import scala.util.Failure
import scala.util.Success

class SettingsView(implicit owner: Ctx.Owner)
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  val tabs = Seq[SettingsTab](
    new GeneralSettings(this),
    new PythonSettings(this)
  )

  val activeTabIdx = Var[Option[Int]](None)
  val activeTab = activeTabIdx.map { _.map { tabs(_) } }

  var registry: Map[String, String] = Map.empty

  Vizier.api
        .configGetRegistry()
        .onComplete {
          case Failure(err) => Vizier.error(err.getMessage())
          case Success(incoming) => registry = incoming
                                    tabs.foreach { _.load() }
                                    activeTabIdx() = Some(0)
        }


  val root = 
    div(`class` := "settings",
      div(`class` := "tabs",
        tabs.zipWithIndex.map { case (tab, idx) =>
          Rx { 
            if(activeTabIdx().isDefined && activeTabIdx().get == idx) { 
              div(`class` := "tab active", tab.title,
                onclick := { evt:dom.Event => evt.stopPropagation() }
              )
            } else {
              div(`class` := "tab inactive", tab.title, 
                onclick := { evt:dom.Event => activeTabIdx() = Some(idx); evt.stopPropagation() }
              )
            }
          }.reactive
        },
        div(`class` := "spacer")
      ),
      div(`class` := "content",
        activeTab.map { 
          case None => div(`class` := "loading", Spinner(30)).render
          case Some(content) => content.root
        }.reactive
      ),

    )
}