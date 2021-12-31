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

object MoveRow extends VizualCommand
{
  def name: String = "Move Row"
  def vizualParameters: Seq[Parameter] = Seq(
    RowIdParameter(id = "row", name = "Row"),
    IntParameter(id = "position", name = "Position")
  )
  def format(arguments: Arguments): String = 
    s"MOVE ROW ${arguments.pretty("row")} IN DATASET ${arguments.get[String]("dataset")} TO POSITION ${arguments.get[Int]("position")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.MoveRow(
        row = arguments.get[String]("row"),
        position = arguments.get[Int]("position")
      )
    )
}

