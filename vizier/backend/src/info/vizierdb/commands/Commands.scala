/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.commands

import play.api.libs.json._
import info.vizierdb.VizierException
import info.vizierdb.serialized

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
    pkg.register(commands:_*)
  } 

  def apply(packageId: String): Package = packages(packageId)


  def describe: Seq[serialized.PackageDescription] =
    describe { (_:String,_:String) => Some(false) }

  def describe(
    suggest: (String, String) => Option[Boolean]
  ): Seq[serialized.PackageDescription] =
  {
    packages.values.map { pkg => 
      serialized.PackageDescription(
        category = pkg.category,
        id = pkg.id,
        name = pkg.name,
        commands = 
          pkg.commands.map { case (commandId, command) =>
            serialized.PackageCommand(
              id = commandId,
              name = command.name,
              parameters = Parameter.describe(command.parameters),
              suggest = suggest(pkg.id, commandId),
              hidden = Some(command.hidden)
            )
          }.toSeq
      )
    }.toSeq
  }

  ///////////////// Hardcoded Command Definitions ///////////////////
  // Commands must be registered below to be visible to the system.
  // 
  // A command is identified by packageId.commandId.  For example, the LoadDataset command is 
  // identified as 'data.load'
  //
  // Registered commands are automatically available to the UI (via the ServiceDescriptor API)
  // 
  // However, the UI special-cases several commonly used commands (e.g., data vis) to have icons
  // and be displayed more prominently.  By default, commands will be placed in the "specialized" 
  // area of the new command tab.  To add an icon and more prominent display, see:
  //   vizier/ui/src/info/vizierdb/ui/components/CommandList.scala
  //

  register(packageId = "data", name = "Data", category = "data")(
    "load"       -> info.vizierdb.commands.data.LoadDataset,
    "unload"     -> info.vizierdb.commands.data.UnloadDataset,
    "clone"      -> info.vizierdb.commands.data.CloneDataset,
    "empty"      -> info.vizierdb.commands.data.EmptyDataset, 
    "checkpoint" -> info.vizierdb.commands.data.CheckpointDataset,
    "unloadFile" -> info.vizierdb.commands.data.UnloadFile,
    "parameters" -> info.vizierdb.commands.data.DeclareParameters,
    "spreadsheet"-> info.vizierdb.commands.data.SpreadsheetCommand,
  )

  register(packageId = "transform", name = "Transformation", category = "data")(
    "aggregate"  -> info.vizierdb.commands.transform.Aggregate,
    "filter"     -> info.vizierdb.commands.transform.Filter,
    "split"      -> info.vizierdb.commands.transform.SplitDataset,
  )

  register(packageId = "plot", name = "Data Plotting", category = "plot")(
    "chart"        -> info.vizierdb.commands.plot.SimpleChart,
    "line-chart"   -> info.vizierdb.commands.plot.LineChart,
    "scatterplot"  -> info.vizierdb.commands.plot.ScatterPlot,
    "scatterplot2" -> info.vizierdb.commands.plot.ScatterPlot,
    "cdf"          -> info.vizierdb.commands.plot.CDFPlot,
    "geo"          -> info.vizierdb.commands.plot.GeoPlot,
    "bar-chart"   -> info.vizierdb.commands.plot.BarChart,
    "barchart"    -> info.vizierdb.commands.plot.BarChart,
  )

  register(packageId = "sql", name = "SQL", category = "code")(
    "query"  -> info.vizierdb.commands.sql.Query
  )

  register(packageId = "script", name = "Scripts", category = "code")(
    "python"  -> info.vizierdb.commands.python.Python,
    "scala"   -> info.vizierdb.commands.jvmScript.ScalaScript,
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
    "sortDataset"  -> info.vizierdb.commands.vizual.SortDataset,
    "script"       -> info.vizierdb.commands.vizual.Script,
  )

  register(packageId = "mimir", name = "Lenses", category = "mimir")(
    "repair_key"     -> info.vizierdb.commands.mimir.RepairKey,
    "missing_value"  -> info.vizierdb.commands.mimir.MissingValue,
    "missing_key"    -> info.vizierdb.commands.mimir.RepairSequence,
    // Dec 18, 2021 by OK: Merge columns isn't really used and is pretty heavyweight.  
    // Proposal: Add a new "Vertical Merge" datasets lens.
    // "picker"         -> info.vizierdb.commands.mimir.MergeColumns,
    "type_inference" -> info.vizierdb.commands.mimir.TypeInference,
    "shape_watcher"  -> info.vizierdb.commands.mimir.ShapeWatcher,
    "comment"        -> info.vizierdb.commands.mimir.Comment,
    "pivot"          -> info.vizierdb.commands.mimir.Pivot,
    "geotag"         -> info.vizierdb.commands.mimir.Geotag,
  )
}

