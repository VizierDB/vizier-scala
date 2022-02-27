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

class Workflow(val subscription: BranchSubscription, val project: Project)
              (implicit owner: Ctx.Owner)
{

  val moduleViews = 
    subscription.modules
                .rxMap { module => new Module(module, this) }

  val moduleViewsWithEdits = new TentativeEdits(moduleViews, project)

  val moduleNodes =
    RxBufferView(ul(`class` := "module_list"), 
      moduleViewsWithEdits.rxMap { 
        case Left(module) => module.root
        case Right(edit) => edit.root
      }
    )




  val root = 
    div(id := "workflow",
      Rx { 
        if(subscription.awaitingReSync()) { div("Syncing workflow...") } 
        else { span("") }
      }.reactive,
      moduleNodes.root,
      div(
        `class` := "add_cell_end_wrapper",
        button(
          `class` := "add_cell_end",
          onclick := { (e: dom.MouseEvent) => moduleViewsWithEdits.appendTentative() }, 
          "+"
        ),
        moduleViewsWithEdits
          .rxLength
          .map { 
            case 0 => div(`class` := "hint",
                          "↑", br(), "Click here to add a module")
            case _ => div()
          }.reactive

      )
    )
}

