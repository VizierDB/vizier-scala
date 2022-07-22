package info.vizierdb.ui.components.settings

import rx._
import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.rxExtras.implicits._

class SettingsView(implicit owner: Ctx.Owner)
{
  val tabs = Seq[SettingsTab](
    APIKeys,
    PythonSettings
  )

  val activeTabIdx = Var[Int](0)
  val activeTab = activeTabIdx.map { tabs(_) }

  val root = 
    div(`class` := "settings",
      div(`class` := "tabs",
        tabs.zipWithIndex.map { case (tab, idx) =>
          Rx { 
            if(idx == activeTabIdx()) { 
              div(`class` := "tab active", tab.title,
                onclick := { evt:dom.Event => evt.stopPropagation() }
              )
            } else {
              div(`class` := "tab inactive", tab.title, 
                onclick := { evt:dom.Event => activeTabIdx() = idx; evt.stopPropagation() }
              )
            }
          }.reactive
        },
        div(`class` := "spacer")
      ),
      div(`class` := "content",
        activeTab.map { _.root }.reactive
      ),

    )
}