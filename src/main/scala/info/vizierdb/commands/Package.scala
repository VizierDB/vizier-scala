package info.vizierdb.commands

case class Package(id: String, name: String, category: String)
{
  val commands =
    scala.collection.mutable.Map[String, Command]()

  def getOption(commandId: String): Option[Command] =
    commands.get(commandId)
  def get(commandId: String): Command =
    commands(commandId)

  def register(commandId: String, command: Command)
  {
    commands.put(commandId, command)
  }
}