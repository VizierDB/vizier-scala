package info.vizierdb.ui.state

import rx._
import info.vizierdb.ui.rxExtras.{ RxBufferVar, RxBuffer, RxBufferView }
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.types._

class ModuleSubscription(initial: ModuleDescription)
{
  def id = initial.id
  val state = Var(ExecutionState(initial.statev2))
  def command = initial.command
  def text = Var(initial.text)
  def links = initial.links
  val messages:RxBufferVar[Message] = RxBuffer[Message]( (
    initial.outputs.stdout.map { (_, StreamType.STDOUT) } ++
    initial.outputs.stderr.map { (_, StreamType.STDERR) }
  ).map { msg => new Message(msg._1, msg._2) }:_* )
  println(s"${messages.length} Messages")
}