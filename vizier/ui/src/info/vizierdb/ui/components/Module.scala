package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.network.ModuleSubscription
import rx._
import info.vizierdb.types.ArtifactType
import info.vizierdb.util.Logging
import info.vizierdb.types

class Module(subscription: ModuleSubscription)
            (implicit owner: Ctx.Owner)
  extends Object
  with Logging
{
  def id = subscription.id

  val outputs = subscription.outputs

  logger.trace(s"creating module view: $this")
  val messages = 
    subscription.messages
                .rxMap { message => Message(message) }
  val messageView = RxBufferView(ul(), messages.rxMap { _.root })
  logger.trace(s"${messageView.root.childNodes.length} messages rendered")

  val root = li(
    div(Rx { pre(subscription.text()) }),
    div(Rx { "State: " + subscription.state() }),
    div("Outputs: ", Rx { outputs.map { _.keys.mkString(", ") }}),
    div("Messages: ", messageView.root),
    div("Menu: ", 
      button("Add Cell Above"),
      button("Add Cell Below"),
      button("Edit Cell"),
      Rx { 
        if (subscription.state() == types.ExecutionState.FROZEN) { 
          button("Thaw Cell", onclick := { (_:dom.MouseEvent) => subscription.thawCell() })
        } else { 
          button("Freeze Cell", onclick := { (_:dom.MouseEvent) => subscription.freezeCell() })
        }
      },
      Rx { 
        if (subscription.state() == types.ExecutionState.FROZEN) {
          button("Thaw Upto Here", onclick := { (_:dom.MouseEvent) => subscription.thawUpto() }) 
        } else { 
          button("Freeze From Here", onclick := { (_:dom.MouseEvent) => subscription.freezeFrom() })
        }
      },
      button("Delete Cell", 
        onclick := { (_:dom.MouseEvent) => subscription.delete() })
    )
  )
}