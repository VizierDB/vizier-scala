package info.vizierdb.commands.data

import play.api.libs.json.JsValue
import info.vizierdb.commands._

object UnloadDataset extends Command
{
  def name: String = ???
  def parameters: Seq[Parameter] = ???
  def format(arguments: Arguments): String = ???
  def process(arguments: Arguments, context: ExecutionContext): Unit = ???
}