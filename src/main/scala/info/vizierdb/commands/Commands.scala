package info.vizierdb.commands

object Commands
{
  val commands = scala.collection.mutable.Map[String, Command]()

  def getOption(packageId: String, commandId: String): Option[Command] =
    commands.get(s"${packageId}.${commandId}")
  def get(packageId: String, commandId: String): Command =
    getOption(packageId, commandId).get

  def register(packageId: String, commandId: String, command: Command): Unit = 
    commands.put(s"${packageId}.${commandId}", command)
  def register(packageId: String, commands: Iterable[(String, Command)]): Unit =
    for((commandId, command) <- commands){ register(packageId, commandId, command) }
  def register(packageId: String)(commands: (String, Command)*): Unit =
    register(packageId, commands)


  register("data")(
    "load"   -> info.vizierdb.commands.data.LoadDataset,
    "unload" -> info.vizierdb.commands.data.UnloadDataset,
  )
}