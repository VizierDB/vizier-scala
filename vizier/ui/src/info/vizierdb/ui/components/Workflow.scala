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

class Workflow(subscription: BranchSubscription, project: Project)
              (implicit owner: Ctx.Owner)
{

  val moduleViews = 
    subscription.modules
                .rxMap { module => new Module(module, this) }

  val moduleViewsWithEdits = new TentativeEdits(moduleViews, project)

  val moduleNodes =
    RxBufferView(ul(), 
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
      },
      moduleNodes.root,
      div(
        button(
          onclick := { (e: dom.MouseEvent) => moduleViewsWithEdits.appendTentative() }, 
          "Add A Cell"
        )
      )
    )
}

