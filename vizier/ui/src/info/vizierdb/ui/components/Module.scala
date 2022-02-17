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
import info.vizierdb.ui.widgets.FontAwesome
import java.awt.Font

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

  val highlight = Var[Boolean](false)

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

  val root: dom.html.LI = li(
    attr("id") := id_attr,
    `class` := "module",
    div(
      `class` := "menu",
      button(
        FontAwesome("chevron-up"),
        br(),
        FontAwesome("plus"),
        onclick := { (_:dom.MouseEvent) => subscription.addCellAbove(workflow) },
        tag("tooltip")("Add cell above")
      ),
      button(
        FontAwesome("pencil-square-o"), 
        onclick := { _:dom.MouseEvent => openEditor() },
        tag("tooltip")("Edit cell")
      ),
      Rx { 
        if (subscription.state() == types.ExecutionState.FROZEN) { 
          button(
            FontAwesome("play"),
            onclick := { (_:dom.MouseEvent) => subscription.thawCell() },
            tag("tooltip")("Thaw this cell")
          )
        } else { 
          button(
            FontAwesome("snowflake-o"), 
            onclick := { (_:dom.MouseEvent) => subscription.freezeCell() },
            tag("tooltip")("Freeze this cell")
          )
        }
      }.reactive,
      Rx { 
        if (subscription.state() == types.ExecutionState.FROZEN) {
          button(
            FontAwesome("chevron-up"),
            br(),
            FontAwesome("play"), 
            onclick := { (_:dom.MouseEvent) => subscription.thawUpto() },
            tag("tooltip")("Thaw this cell and all above")
          ) 
        } else { 
          button(
            FontAwesome("snowflake-o"), 
            br(),
            FontAwesome("chevron-down"),
            onclick := { (_:dom.MouseEvent) => subscription.freezeFrom() },
            tag("tooltip")("Freeze this cell and all below")
          )
        }
      }.reactive,
      button(
        FontAwesome("trash-o"), 
        onclick := { (_:dom.MouseEvent) => subscription.delete() },
        tag("tooltip")("Delete this cell")
      ),
      button(
        FontAwesome("plus"),
        br(),
        FontAwesome("chevron-down"),
        onclick := { (_:dom.MouseEvent) => subscription.addCellBelow(workflow) },
        tag("tooltip")("Add cell below")
      ),
    ),
    div(
      `class` := "module_body",
      div(Rx { 
        editor().map { _.root }.getOrElse { pre(subscription.text()) }
      }.reactive),
      div(Rx { "State: " + subscription.state() }.reactive),
      div("Outputs: ", outputs.map { _.keys.mkString(", ") }.reactive),
      div("Messages: ", messageView.root),
    )
  ).render

  def refreshClasses() =
  {
    val currentState = subscription.state.now.toString.toLowerCase
    val highlightState = if(highlight.now){ " highlight" } else { "" }
    root.className = s"module ${currentState}_state $highlightState"
  }

  subscription.state.trigger { refreshClasses() }
  highlight.trigger { refreshClasses() }
}