package info.vizierdb.ui.network

import rx._
import info.vizierdb.ui.rxExtras.{ RxBufferVar, RxBuffer, RxBufferView }
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.types._
import info.vizierdb.util.Logging
import info.vizierdb.serialized
import info.vizierdb.serializers._
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.ui.components.Module
import info.vizierdb.ui.components.TentativeEdits


class ModuleSubscription(
  initial: serialized.ModuleDescription, 
  branch: BranchSubscription,
  var position: Int
)
  extends Object
  with Logging
{
  def id: Identifier = initial.moduleId
  val state = Var(initial.statev2)
  def command = initial.command
  def text = Var(initial.text)
  def links = initial.links
  val outputs = Var[Map[String,Option[serialized.ArtifactSummary]]](
    initial.artifacts.map { x => x.name -> Some(x) }.toMap
  )
  val messages:RxBufferVar[serialized.MessageDescriptionWithStream] = 
    RxBuffer[serialized.MessageDescriptionWithStream]( (
      initial.outputs.stdout.map { _.withStream(StreamType.STDOUT) } ++
      initial.outputs.stderr.map { _.withStream(StreamType.STDERR) }
    ):_* )
  logger.debug(s"${messages.length} Messages; ${outputs.now.size} outputs; $outputs")

  /**
   * Delete this module from the workflow
   */
  def delete(): Unit = 
    branch.Client.workflowDelete(position)

  /**
   * Freeze the current cell
   */
  def freezeCell(): Unit = 
    branch.Client.workflowFreezeOne(position)

  /**
   * Freeze all cells starting with current cell
   */
  def freezeFrom(): Unit = 
    branch.Client.workflowFreezeFrom(position)

  /**
   * Thaw the current cell
   */
  def thawCell(): Unit = 
    branch.Client.workflowThawOne(position)

  /**
   * Thaw all cells upto current cell
   */
  def thawUpto(): Unit = 
    branch.Client.workflowThawUpto(position)
}