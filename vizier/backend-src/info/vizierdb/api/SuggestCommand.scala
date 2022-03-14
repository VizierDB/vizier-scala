package info.vizierdb.api

import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.serialized
import info.vizierdb.types._
import info.vizierdb.commands.Commands

object SuggestCommand
  extends Object
    with LazyLogging
{

  val DEFAULTS = Set(
    "data.load",
    "data.unload",
    "plot.chart",
    "docs.markdown",
    "script.python",
    "script.scala",
    "sql.query"
  )

  def apply(
    projectId: Identifier,
    branchId: Identifier,
    before: Option[Identifier], 
    after: Option[Identifier],
    workflowId: Option[Identifier] = None,
  ): Seq[serialized.PackageDescription] =
  {
    // Placeholder for now.  Suggest the common commands: 
    Commands.describe {
      (packageId, commandId) => 
        Some(DEFAULTS(s"$packageId.$commandId"))
    }
  }
}