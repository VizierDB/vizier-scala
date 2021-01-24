/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
import org.mimirdb.vizual

object UpdateCell extends VizualCommand
{
  def name: String = "Update Cell"
  def vizualParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "column", name = "Column"),
    StringParameter(id = "row", name = "Row (optional)", required = false, relaxed = true),
    StringParameter(id = "value", name = "Value", required = false)
  )
  def format(arguments: Arguments): String = 
    s"UPDATE ${arguments.get[String]("dataset")}[${arguments.pretty("column")}${arguments.getOpt[String]("row").map { ":"+_ }.getOrElse{""}}] UPDATE ${arguments.pretty("value")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
  {
    val rowSelection = arguments.getOpt[String]("row") match {
      case Some(row) if row.startsWith("=") 
                     => vizual.RowsByConstraint(row.substring(1))
      case Some(row) => vizual.RowsById(Set(row))
      case None      => vizual.AllRows()
    }
    Seq(
      vizual.UpdateCell(
        column = arguments.get[Int]("column"),
        row = Some(rowSelection),
        value = Some(arguments.get[String]("value"))
      )
    )
  }
}

