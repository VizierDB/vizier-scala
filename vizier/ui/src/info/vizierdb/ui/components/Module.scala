package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.network.ModuleSubscription
import rx._
import info.vizierdb.types._
import info.vizierdb.util.Logging
import info.vizierdb.types
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.network.BranchWatcherAPIProxy
import info.vizierdb.serialized.ArtifactSummary

class Module(val subscription: ModuleSubscription, workflow: Workflow)
            (implicit owner: Ctx.Owner)
  extends Object
  with Logging
  with ModuleEditorDelegate
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  def id = tentativeModuleId.getOrElse { subscription.id }

  def realModuleId: Option[Identifier] = Some(id)
  
  var tentativeModuleId: Option[Identifier] = None

  def setTentativeModuleId(newId: Identifier) = tentativeModuleId = Some(newId)

  val visibleArtifacts = Var[Rx[Map[String, ArtifactSummary]]](Var(Map.empty))

  val outputs = subscription.outputs

  def command = subscription.command

  def toc = subscription.toc

  def id_attr = s"module_$id"

  logger.trace(s"creating module view: $this")
  val messages = 
    subscription.messages
                .rxMap { message => Message(message) }
  val messageView = RxBufferView(ul(), messages.rxMap { _.root })
  logger.trace(s"${messageView.root.childNodes.length} messages rendered")

  val editor = Var[Option[ModuleEditor]](None)

  def openEditor(): Unit =
  { 
    val packageId = subscription.command.packageId
    val commandId = subscription.command.commandId
    subscription
      .branch
      .api
      .packages
      .onSuccess { case packages =>
        val command = 
          packages.find { _.id == packageId } 
                  .flatMap { _.commands.find { _.id == commandId } }
                  .getOrElse { Vizier.error(s"This server doesn't support editing $packageId.$commandId") }
        val tempEditor = 
          new ModuleEditor(
            packageId = packageId,
            command = command,
            delegate = this
          )
        tempEditor.loadState(subscription.command.arguments)
        editor() = Some(tempEditor)
      }
  }

  def cancelEditor(): Unit = 
  {
    editor() = None
  }

  def client: BranchWatcherAPIProxy = 
    subscription
      .branch
      .Client

  def isLast: Boolean = 
    subscription.position == subscription.branch.modules.size

  def position: Int =
    subscription.position

  val root = li(
    attr("id") := id_attr,
    div(Rx { 
      editor().map { _.root }.getOrElse { pre(subscription.text()) }
    }),
    div(Rx { "State: " + subscription.state() }),
    div("Outputs: ", Rx { outputs.map { _.keys.mkString(", ") }}),
    div("Menu: ", 
      button("Add Cell Above", onclick := { (_:dom.MouseEvent) => subscription.addCellAbove(workflow) }),
      button("Add Cell Below", onclick := { (_:dom.MouseEvent) => subscription.addCellBelow(workflow) }),
      button("Edit Cell", onclick := { _:dom.MouseEvent => openEditor() }),
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
    ),
    div("Messages: ", messageView.root),
  )
}