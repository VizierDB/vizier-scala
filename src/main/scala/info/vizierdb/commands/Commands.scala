package info.vizierdb.commands

import play.api.libs.json._

object Commands
{
  val packages = 
    scala.collection.mutable.Map[String, Package]()

  def getOption(packageId: String, commandId: String): Option[Command] =
    packages.get(packageId)
            .flatMap { _.getOption(commandId) }
  def get(packageId: String, commandId: String): Command =
    getOption(packageId, commandId).get

  def register(packageId: String, name: String, category: String)(commands: (String, Command)*): Unit =
  {
    val pkg = Package(packageId, name, category)
    packages.put(packageId, pkg)
    for((commandId, command) <- commands){
      pkg.register(commandId, command)
    }
  } 

  def toJson: JsValue =
  {
    JsArray(
      packages.values.map { pkg => 
        Json.obj(
          "category" -> pkg.category,
          "id"       -> pkg.id,
          "name"     -> pkg.name,
          "commands" -> JsArray(
            pkg.commands.map { case (commandId, command) =>
              var idx = 0
              Json.obj(
                "id" -> commandId,
                "name" -> command.name,
                "parameters" -> Parameter.describe(command.parameters),
                "suggest" -> false
              )
            }.toSeq
          ),
        )
      }.toSeq
    )
  }


  register(packageId = "data", name = "Data", category = "data")(
    "load"   -> info.vizierdb.commands.data.LoadDataset,
    "unload" -> info.vizierdb.commands.data.UnloadDataset,
    "clone"  -> info.vizierdb.commands.data.CloneDataset,
    "empty"  -> info.vizierdb.commands.data.EmptyDataset, 
  )

  register(packageId = "sql", name = "SQL", category = "code")(
    "query"  -> info.vizierdb.commands.sql.Query
  )

  register(packageId = "script", name = "Scripts", category = "code")(
    "python"  -> info.vizierdb.commands.python.Python
  )

  register(packageId = "docs", name = "Documentation", category = "docs")(
    "markdown" -> info.vizierdb.commands.markdown.Markdown
  )

  register(packageId = "sample", name = "Dataset Sampling", category = "data")(
    "basic_sample"                -> info.vizierdb.commands.sample.BasicSample,
    "automatic_stratified_sample" -> info.vizierdb.commands.sample.AutomaticStratifiedSample,
    "manual_stratified_sample"    -> info.vizierdb.commands.sample.ManualStratifiedSample
  )

  register(packageId = "vizual", name = "Vizual", category = "vizual")(
    "deleteColumn" -> info.vizierdb.commands.vizual.DeleteColumn
  )
}