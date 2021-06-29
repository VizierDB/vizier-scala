package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.network.ModuleSubscription
import rx._
import info.vizierdb.types.ArtifactType
import info.vizierdb.util.Logging

class Module(subscription: ModuleSubscription)
            (implicit owner: Ctx.Owner)
  extends Object
  with Logging
{
  def id = subscription.id

  val outputs = subscription.outputs

  logger.trace(s"creating module view: $this")
  val messages = 
    RxBufferView(
      ul(), 
      subscription.messages
                  .rxMap { message => li(Message(message)) }
    )
  logger.trace(s"${messages.root.childNodes.length} messages rendered")

  val root = li(
    div(Rx { pre(subscription.text()) }),
    div(Rx { "State: " + subscription.state() }),
    div("Outputs: ", Rx { outputs.map { _.keys.mkString(", ") }}),
    div("Messages: ", messages.root),
  )
}