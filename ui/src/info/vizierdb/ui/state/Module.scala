package info.vizierdb.ui.state

import scala.scalajs.js
import scalatags.JsDom.all._

@js.native
trait CommandDescription extends js.Object
{
  val packageId: String = js.native
  val commandId: String = js.native
  // val arguments: js.Array[js.Dictionary[js.Dynamic]] = js.native
}

@js.native
trait ModuleOutputDescription extends js.Object
{
  val stdout: js.Array[MessageDescription] = js.native
  val stderr: js.Array[MessageDescription] = js.native
}

@js.native
trait ModuleDescription extends js.Object
{
  val id: String = js.native
  val state: Int = js.native
  val statev2: Int = js.native
  val command: CommandDescription = js.native
  val text: String = js.native
  val links: js.Dictionary[js.Dynamic] = js.native
  val outputs: ModuleOutputDescription = js.native
}

class Module(description: ModuleDescription)
{
  def id = description.id
  def state = description.state
  def command = description.command
  def text = description.text
  def links = description.links 

  val commandString = 
    command.packageId + "." + command.commandId

  def render: Frag = 
    span(commandString)
}