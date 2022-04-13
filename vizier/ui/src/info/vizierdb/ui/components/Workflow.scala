package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.network.BranchSubscription
import rx._
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.RxBufferBase
import info.vizierdb.ui.rxExtras.RxBufferWatcher
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.types.ArtifactType
import info.vizierdb.ui.widgets.FontAwesome

class Workflow(val subscription: BranchSubscription, val project: Project)
              (implicit owner: Ctx.Owner)
{

  val moduleViews = 
    subscription.modules
                .rxMap { module => new Module(module, this) }

  val moduleViewsWithEdits = new TentativeEdits(moduleViews, project, this)

  val moduleNodes =
    RxBufferView(div(`class` := "module_list"), 
      moduleViewsWithEdits.rxMap { 
        case Left(module) => 
          div(
            module.root,
            InterModule(
              button(
                FontAwesome("plus"),
                onclick := { _:dom.Event => 
                  moduleViewsWithEdits.insertTentative(module.subscription.position + 1)
                }
              ).render,
              button(
                FontAwesome("binoculars"),
                onclick := { _:dom.Event => println("TODO: Create Inspector") }
              ).render
            ),
          )
        case Right(edit) => edit.root
      }
    )

  // DEBUG: Automatically add a tentative module
  // {
  //   subscription.awaitingReSync.triggerLater { f => 
  //     if(!f) { moduleViewsWithEdits.appendTentative() }
  //   }
  // }


  /**
   * A helper method to create divs representing inter-module separators
   */
  def InterModule(buttons: dom.html.Button*): Frag =
  {
    div(`class` := "inter_module",
      div(
        `class` := "elements", 
        span(`class` := "separator", "—"),
        buttons,
        span(`class` := "separator", "—"),
      )
    )
  }


  val root = 
    div(id := "workflow",
      Rx { 
        if(subscription.awaitingReSync()) { div("Syncing workflow...") } 
        else { 
          div(`class` := "first",
            InterModule(
              button(
                FontAwesome("plus"),
                onclick := { _:dom.Event => moduleViewsWithEdits.insertTentative(0) }
              ).render
            ),
            moduleViewsWithEdits
              .rxLength
              .map { 
                case 0 => div(`class` := "hint",
                              "↑", br(), "Click here to add a module")
                case _ => div()
              }.reactive
          )
        }
      }.reactive,
      moduleNodes.root,
    )
}

