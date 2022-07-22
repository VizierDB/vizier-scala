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
import info.vizierdb.util.StringUtils
import java.awt.Font
import info.vizierdb.ui.widgets.Tooltip
import info.vizierdb.ui.widgets.PopUpButton
import info.vizierdb.ui.widgets.ScrollIntoView

class Module(val subscription: ModuleSubscription)
            (implicit owner: Ctx.Owner)
  extends WorkflowElement
  with Logging
  with ModuleEditorDelegate
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  /**
   * The working identifier of the module, the real one or the tentative
   * one if the module is before its initial commit.
   */
  def id = tentativeModuleId.getOrElse { subscription.id }

  /**
   * The "real" module identifier (this or tentativeModuleId must be Some)
   */
  def realModuleId: Option[Identifier] = Some(id)
  
  /**
   * The tentative module identifier (this or realModuleId must be Some)
   */
  var tentativeModuleId: Option[Identifier] = None

  /**
   * Update the tentative module ID (once it is allocated)
   */
  def setTentativeModuleId(newId: Identifier) = tentativeModuleId = Some(newId)

  /**
   * A reactive list of all of the outputs produced by this cell
   */
  val outputs = subscription.outputs

  /**
   * The current execution state of the cell
   */
  val executionState = subscription.state

  /**
   * True if the cell should be emphasized, visually
   * 
   * Currently, this is used by the table-of-contents to highlight cells
   * that are moused over in the ToC
   */
  val highlight = Var[Boolean](false)

  /**
   * The table of contents summary for the command represented by this module
   */
  def toc = subscription.toc

  /**
   * The DOM id of the tag encoding this module.
   */
  def id_attr = s"module_$id"

  logger.trace(s"creating module view: $this")

  /**
   * A reactive list of all messages displayed with this module
   */
  val messages = 
    subscription.messages
                .rxMap { message => Message(message) }
  
  /**
   * A reactive DOM node of all of the messages displayed with this module
   */
  val messageView = RxBufferView(ul(`class` := "messages"), messages.rxMap { _.root })
  logger.trace(s"${messageView.root.childNodes.length} messages rendered")

  /**
   * A reactive option containing the editor for this module if it should
   * be in editing mode, and None otherwise.
   */
  val editor = Var[Option[ModuleEditor]](None)

  /**
   * True for "special" modules that should have their summary text hidden
   */
  val hideSummary: Boolean = 
    subscription.packageId == "docs"

  /**
   * Retrieve a moduledescription for this module
   */
  def description = subscription.description

  /**
   * Allocate an editor for the module and place it in editing mode.
   */
  def openEditor(): Unit =
  { 
    val packageId = subscription.packageId
    val commandId = subscription.commandId
    Vizier.api
      .packages
      .onSuccess { case packages =>
        val command = 
          packages.find { _.id == packageId } 
                  .flatMap { _.commands.find { _.id == commandId } }
                  .getOrElse { Vizier.error(s"This server doesn't support editing $packageId.$commandId") }
        val tempEditor = 
          ModuleEditor(
            packageId = packageId,
            command = command,
            delegate = this
          )
        tempEditor.loadState(subscription.arguments)
        editor() = Some(tempEditor)
      }
  }

  /**
   * Abort editing mode (if active)
   */
  def cancelEditor(): Unit = 
  {
    editor() = None
  }

  /**
   * A connection to the server
   */
  def client: BranchWatcherAPIProxy = 
    subscription.client

  /**
   * This module's position in the workflow
   */
  def position: Var[Int] =
    subscription.position

  /**
   * The DOM node representing this module
   */
  val root: dom.html.Div = div(
    attr("id") := id_attr,
    `class` := "module",
    div(
      `class` := "menu",
      if(subscription.isEditable){ Seq(
        button(
          FontAwesome("pencil-square-o"), 
          onclick := { _:dom.MouseEvent => openEditor() },
          Tooltip("Edit cell")
        ),
        PopUpButton(
          button(
            subscription.state
                        .map { _ == types.ExecutionState.FROZEN }
                        .map { f => (if(f) { FontAwesome("play") } 
                                     else  { FontAwesome("snowflake-o") }) }
                        .reactive
          ).render,
          button(
            FontAwesome("arrow-circle-left"),
            onclick := { (_:dom.MouseEvent) => 
              if(subscription.state.now == types.ExecutionState.FROZEN){ subscription.thawCell() } 
              else                                                     { subscription.freezeCell() }
            }
          ).render,
          button(
            subscription.state
                        .map { _ == types.ExecutionState.FROZEN }
                        .map { if(_) { FontAwesome("arrow-circle-up") } 
                               else  { FontAwesome("arrow-circle-down") } }
                        .reactive,
            onclick := { (_:dom.MouseEvent) => 
              if(subscription.state.now == types.ExecutionState.FROZEN){ subscription.thawCell() } 
              else                                                     { subscription.freezeCell() }
            }
          ).render,
        ),
        PopUpButton(
          button(
            `class` := "to_confirm",
            FontAwesome("trash-o"), 
            Tooltip("Delete this cell")
          ).render,
          button(
            `class` := "confirm",
            FontAwesome("check"),
            onclick := { _:dom.Event => subscription.delete(); }
          ).render
        ),
        div(`class` := "spacer")
      )} else { Seq.empty }:Seq[Frag]
    ),
    div(
      `class` := "module_body",
      Rx { 
        editor().map { ed => div(`class` := "editor", ed.root) }
                .getOrElse { 
                  div(
                    `class` := (if(hideSummary) { "summary hidden" } else { "summary" }),
                    pre(
                      subscription.text(),
                      if(subscription.isEditable){
                        Seq(onclick := { _:dom.MouseEvent => openEditor() })
                      } else { Seq.empty }:Seq[Frag],
                    )
                  )
                }
      }.reactive,
      div(`class` := (if(hideSummary) { "status hidden" } else { "status" }), 
        span(`class` := "label", "Status: "),
        Rx { 
          val timestamps = subscription.timestamps()
          subscription.state() match {
            case ExecutionState.DONE => 
              span(span(`class` := "state", "Completed"), 
                   s" ${timestamps.finishedAtString} (${timestamps.runtimeString})")
            case ExecutionState.FROZEN => 
              span(span(`class` := "state", "Frozen"),
                   s" ${timestamps.createdAtString}")
            case ExecutionState.CANCELLED | ExecutionState.ERROR =>
              span(`class` := "state", StringUtils.capitalize(subscription.state().toString()))
            case ExecutionState.RUNNING =>
              span(span(`class` := "state", "Running"),
                   s" since ${timestamps.startedAtString}")
            case ExecutionState.WAITING | ExecutionState.STALE =>
              span(span(`class` := "state", "Waiting"),
                   s" to run since ${timestamps.createdAtString}")
          }
        }.reactive
      ),
      div(
        `class` := "messages",
        (if(hideSummary && subscription.isEditable) { onclick := { _:dom.MouseEvent => openEditor() } }
         else { attr("ignore") := "ignored" }),
        messageView.root
      ),
    )
  ).render

  /**
   * Trigger a refresh of the cell's class
   */
  def refreshClasses() =
  {
    val currentState = subscription.state.now.toString.toLowerCase
    val highlightState = if(highlight.now){ " highlight" } else { "" }
    root.className = s"module ${currentState}_state $highlightState"
  }

  //
  //React to changes in the module state or highlight status by updating
  //the module's classes
  //
  subscription.state.trigger { refreshClasses() }
  highlight.trigger { refreshClasses() }

  override def toString: String =
    s"${subscription.packageId}.${subscription.commandId} @ $position [$id]"
}