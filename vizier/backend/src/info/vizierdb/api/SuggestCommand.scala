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