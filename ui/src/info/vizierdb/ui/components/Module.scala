package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.network.ModuleSubscription
import rx._
import info.vizierdb.types.ArtifactType

class Module(subscription: ModuleSubscription)
            (implicit owner: Ctx.Owner)
{
  def id = subscription.id

  val outputs = subscription.outputs

  println(s"creating module view: $this")
  val messages = 
    RxBufferView(
      ul(), 
      subscription.messages
                  .rxMap { message => li(Message(message)) }
    )
  println(s"${messages.root.childNodes.length} messages rendered")

  val root = li(
    div(Rx { pre(subscription.text()) }),
    div(Rx { "State: " + subscription.state() }),
    div("Outputs: ", Rx { outputs.map { _.keys.mkString(", ") }}),
    div("Messages: ", messages.root),
  )
}