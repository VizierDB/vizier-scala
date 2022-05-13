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
import info.vizierdb.ui.components.Workflow
import info.vizierdb.ui.components.StaticWorkflow


class ModuleSubscription(
  initial: serialized.ModuleDescription, 
  val branch: Either[BranchSubscription, StaticWorkflow],
  var position: Int
)
  extends Object
  with Logging
{
  var id: Identifier = initial.moduleId
  val state = Var(initial.statev2)
  val commandId = initial.command.commandId
  val packageId = initial.command.packageId
  var arguments = initial.command.arguments
  lazy val text = Var(initial.text)
  val timestamps = Var(initial.timestamps)
  def toc = initial.toc
  val outputs = Var[Map[String,Option[serialized.ArtifactSummary]]](
    initial.artifacts.map { x => x.name -> Some(x) }.toMap
  )
  val messages:RxBufferVar[serialized.MessageDescriptionWithStream] = 
    RxBuffer[serialized.MessageDescriptionWithStream]( (
      initial.outputs.stdout.map { _.withStream(StreamType.STDOUT) } ++
      initial.outputs.stderr.map { _.withStream(StreamType.STDERR) }
    ):_* )
  logger.debug(s"${messages.length} Messages; ${outputs.now.size} outputs; $outputs")

  def isEditable = branch.isLeft

  def client = 
    branch match { 
      case Left(l) => l.Client 
      case Right(_) => throw new IllegalArgumentException("Trying to edit a static module")
    }

  /**
   * Delete this module from the workflow
   */
  def delete(): Unit = client.workflowDelete(position)

  /**
   * Freeze the current cell
   */
  def freezeCell(): Unit = client.workflowFreezeOne(position)

  /**
   * Freeze all cells starting with current cell
   */
  def freezeFrom(): Unit = client.workflowFreezeFrom(position)

  /**
   * Thaw the current cell
   */
  def thawCell(): Unit = client.workflowThawOne(position)

  /**
   * Thaw all cells upto current cell
   */
  def thawUpto(): Unit = client.workflowThawUpto(position)

}