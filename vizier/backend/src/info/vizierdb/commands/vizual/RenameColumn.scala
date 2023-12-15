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
package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import info.vizierdb.spark.vizual

object RenameColumn extends VizualCommand
{
  def name: String = "Rename Column"
  def vizualParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "column", name = "Column"),
    StringParameter(id = "name", name = "New Column Name")
  )
  def format(arguments: Arguments): String = 
    s"RENAME COLUMN ${arguments.pretty("column")} IN ${arguments.get[String]("dataset")} TO ${arguments.get[String]("name")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.RenameColumn(
        column = arguments.get[Int]("column"),
        name = arguments.get[String]("name")
      )
    )
  override def hidden: Boolean = true
}

