package info.vizierdb.ui.network

import rx._
import info.vizierdb.ui.rxExtras.{ RxBufferVar, RxBuffer, RxBufferView }
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.types._
import info.vizierdb.ui.components.Artifact

class ModuleSubscription(initial: ModuleDescription)
{
  def id = initial.id
  val state = Var(ExecutionState(initial.statev2))
  def command = initial.command
  def text = Var(initial.text)
  def links = initial.links
  val outputs = Var[Map[String,Artifact]](
    initial.artifacts.map { a => a.name -> new Artifact(a) }.toMap
  )
  val messages:RxBufferVar[StreamedMessage] = RxBuffer[StreamedMessage]( (
    initial.outputs.stdout.map { (_, StreamType.STDOUT) } ++
    initial.outputs.stderr.map { (_, StreamType.STDERR) }
  ).map { msg => new StreamedMessage(msg._1, msg._2) }:_* )
  println(s"${messages.length} Messages; ${outputs.now.size} outputs; $outputs")
}