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
                .rxMap { module => new Module(module) }

  val moduleViewsWithEdits = new TentativeEdits(moduleViews, project, this)

  val moduleNodes =
    RxBufferView(div(`class` := "module_list"), 
      moduleViewsWithEdits.rxMap { 
        case WorkflowModule(module) => 
          div(
            module.root,
            StandardInterModule(
              moduleViewsWithEdits.sourceToTargetPosition(module.subscription.position)+1
            )
          )
        case WorkflowTentativeModule(edit) => 
          div(
            edit.root,
            StandardInterModule(edit.position+1)
          )
        case WorkflowArtifactInspector(inspect) => 
          div(
            inspect.root,
            StandardInterModule(inspect.position+1)
          )
      }
    )

  // DEBUG: Automatically add a tentative module
  // {
  //   subscription.awaitingReSync.triggerLater { f => 
  //     if(!f) { moduleViewsWithEdits.insertInspector(2) }
  //   }
  // }

  def StandardInterModule(position: => Int): Frag =
    InterModule(
      button(
        FontAwesome("pencil-square-o"),
        onclick := { _:dom.Event => 
          moduleViewsWithEdits.insertTentative(position)
                              .setDefaultModule("docs","markdown")
                              .scrollIntoView()
        }
      ).render,
      button(
        FontAwesome("plus"),
        onclick := { _:dom.Event => 
          moduleViewsWithEdits.insertTentative(position)
                              .scrollIntoView()
        }
      ).render,
      button(
        FontAwesome("binoculars"),
        onclick := { _:dom.Event => 
          moduleViewsWithEdits.insertInspector(position)
                              .scrollIntoView()
        }
      ).render
    )

  /**
   * A helper method to create divs representing inter-module separators
   */
  def InterModule(buttons: dom.html.Button*): Frag =
    div(`class` := "inter_module",
      div(
        `class` := "elements", 
        span(`class` := "separator", "———"),
        buttons,
        span(`class` := "separator", "———"),
      )
    )

  /**
   * The root DOM node of the workflow
   * 
   * Note.  This should closely mirror [[StaticWorkflow]].  Any CSS-related changes applied here
   * should be propagated there as well.
   */
  val root = 
    div(`class` := "workflow_content",
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

