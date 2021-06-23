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

class Workflow(subscription: BranchSubscription)
              (implicit owner: Ctx.Owner)
{

  val moduleViews = 
    subscription.modules
                .rxMap { module => new Module(module) }

  val moduleViewsWithEdits = new TentativeEdits(moduleViews)

  val moduleNodes =
    RxBufferView(ul(), 
      moduleViewsWithEdits.rxMap { 
        case Left(module) => module.root
        case Right(edit) => edit.root
      }
    )


  val root = 
    div(id := "workflow",
      moduleNodes.root,
      div(
        button(
          onclick := { (e: dom.MouseEvent) => moduleViewsWithEdits.appendTentative() }, 
          "Add A Cell"
        )
      )
    )
}

