package info.vizierdb.ui.view

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.state.ModuleSubscription
import rx._

class ModuleView(subscription: ModuleSubscription)
                (implicit owner: Ctx.Owner, data: Ctx.Data)
{
  println("creating module view")
  val messages = 
    RxBufferView(
      ul(),
      subscription.messages
                  .rxMap { message => li(MessageView(message)) }
    )
  println(s"${messages.root.childNodes.length} messages rendered")

  val root = li(
    div(Rx { pre(subscription.text()) }),
    div(Rx { "State: " + subscription.state() }),
    div("Messages: ", messages.root)
  )
}