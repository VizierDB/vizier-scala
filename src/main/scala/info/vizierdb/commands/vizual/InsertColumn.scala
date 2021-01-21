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

object InsertColumn extends VizualCommand
{
  def name: String = "Insert Column"
  def vizualParameters: Seq[Parameter] = Seq(
    IntParameter(id = "position", name = "Position", required = false),
    StringParameter(id = "name", name = "Column Name")
  )
  def format(arguments: Arguments): String = 
    s"INSERT COLUMN ${arguments.get[String]("name")} INTO DATASET ${arguments.get[String]("dataset")}${arguments.getOpt[Int]("position").map {" AT POSITION "+_}.getOrElse{""}}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.InsertColumn(
        position = arguments.getOpt[Int]("position"),
        name = arguments.get[String]("name")
      )
    )
}

