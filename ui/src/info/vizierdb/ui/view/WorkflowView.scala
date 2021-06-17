package info.vizierdb.ui.view

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.state.BranchSubscription
import rx._
import info.vizierdb.ui.rxExtras.RxBufferView

class WorkflowView(subscription: BranchSubscription)
                  (implicit owner: Ctx.Owner, data: Ctx.Data)
{
  val moduleViews = 
    subscription.modules
                .rxMap { module => new ModuleView(module) }
  val moduleNodes =
    RxBufferView(ul(), moduleViews.rxMap { _.root })

  def appendModule()
  {
    println("Would append")
  }

  def root = 
    div(id := "workflow",
      moduleNodes.root,
      div(
        button(
          onclick := { (e: dom.MouseEvent) => appendModule() }, 
          "Add A Cell"
        )
      )
    )
}