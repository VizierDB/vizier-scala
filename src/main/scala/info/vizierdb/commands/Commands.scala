package info.vizierdb.commands

import play.api.libs.json._
import info.vizierdb.VizierException

object Commands
{
  val packages = 
    scala.collection.mutable.Map[String, Package]()

  def getOption(packageId: String, commandId: String): Option[Command] =
    packages.get(packageId)
            .flatMap { _.getOption(commandId) }
  def get(packageId: String, commandId: String): Command =
    getOption(packageId, commandId)
          .getOrElse {
            throw new VizierException(s"Unknown Command '$packageId.$commandId'")
          }

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

  register(packageId = "plot", name = "Data Plotting", category = "plot")(
    "chart"  -> info.vizierdb.commands.plot.SimpleChart
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
    "deleteColumn" -> info.vizierdb.commands.vizual.DeleteColumn,
    "deleteRow"    -> info.vizierdb.commands.vizual.DeleteRow,
    "dropDataset"  -> info.vizierdb.commands.vizual.DropDataset,
    "insertColumn" -> info.vizierdb.commands.vizual.InsertColumn,
    "insertRow"    -> info.vizierdb.commands.vizual.InsertRow,
    "moveColumn"   -> info.vizierdb.commands.vizual.MoveColumn,
    "moveRow"      -> info.vizierdb.commands.vizual.MoveRow,
    "projection"   -> info.vizierdb.commands.vizual.FilterColumns,
    "renameColumn" -> info.vizierdb.commands.vizual.RenameColumn,
    "renameDataset"-> info.vizierdb.commands.vizual.RenameDataset,
    "updateCell"   -> info.vizierdb.commands.vizual.UpdateCell,
    "script"       -> info.vizierdb.commands.vizual.Script,
  )

  register(packageId = "mimir", name = "Lenses", category = "mimir")(
    "repair_key"     -> info.vizierdb.commands.mimir.RepairKey,
    "missing_value"  -> info.vizierdb.commands.mimir.MissingValue,
    "missing_key"    -> info.vizierdb.commands.mimir.RepairSequence,
    "picker"         -> info.vizierdb.commands.mimir.MergeColumns,
    "type_inference" -> info.vizierdb.commands.mimir.TypeInference,
    "shape_watcher"  -> info.vizierdb.commands.mimir.ShapeWatcher,
    "comment"        -> info.vizierdb.commands.mimir.Comment,
    "pivot"          -> info.vizierdb.commands.mimir.Pivot,
    "geocode"        -> info.vizierdb.commands.mimir.Geocode
  )
}