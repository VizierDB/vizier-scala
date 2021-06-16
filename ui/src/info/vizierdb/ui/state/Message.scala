package info.vizierdb.ui.state

import scala.scalajs.js
import info.vizierdb.types._

@js.native
trait MessageDescription extends js.Object
{
  val `type`: String = js.native
  val value: js.Dynamic = js.native
}

class Message(description: MessageDescription, val stream: StreamType.T)
{
  def t = description.`type`
  def value = description.value
}