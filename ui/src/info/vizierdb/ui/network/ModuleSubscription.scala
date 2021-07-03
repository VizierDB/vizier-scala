package info.vizierdb.ui.network

import rx._
import info.vizierdb.ui.rxExtras.{ RxBufferVar, RxBuffer, RxBufferView }
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.types._
import info.vizierdb.ui.components.Artifact
import info.vizierdb.util.Logging
import info.vizierdb.encoding

class ModuleSubscription(
  initial: encoding.ModuleDescription, 
  branch: BranchSubscription,
  var position: Int
)
  extends Object
  with Logging
{
  def id = initial.id
  val state = Var(ExecutionState(initial.statev2))
  def command = initial.command
  def text = Var(initial.text)
  def links = initial.links
  val outputs = Var[Map[String,Artifact]](
    initial.artifacts.map { a => a.name -> new Artifact(a) }.toMap
  )
  val messages:RxBufferVar[encoding.StreamedMessage] = RxBuffer[encoding.StreamedMessage]( (
    initial.outputs.stdout.map { (_, StreamType.STDOUT) } ++
    initial.outputs.stderr.map { (_, StreamType.STDERR) }
  ).map { msg => new encoding.StreamedMessage(msg._1, msg._2) }:_* )
  logger.debug(s"${messages.length} Messages; ${outputs.now.size} outputs; $outputs")

  /**
   * Delete this module from the workflow
   */
  def delete(): Unit = branch.deleteModule(position)
}