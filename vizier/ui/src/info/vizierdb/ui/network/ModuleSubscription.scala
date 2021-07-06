package info.vizierdb.ui.network

import rx._
import info.vizierdb.ui.rxExtras.{ RxBufferVar, RxBuffer, RxBufferView }
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.types._
import info.vizierdb.util.Logging
import info.vizierdb.serialized
import info.vizierdb.serializers._
import autowire._
import info.vizierdb.api.websocket.BranchWatcherAPI
import scala.concurrent.ExecutionContext.Implicits.global


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
    branch.Client[BranchWatcherAPI]
          .workflowDelete(position)
          .call()
}