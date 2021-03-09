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

object RenameDataset extends Command
{
  def name: String = "Rename Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    StringParameter(id = "name", name = "New Dataset Name")
  )
  def format(arguments: Arguments): String = 
    s"RENAME ${arguments.get[String]("dataset")} TO ${arguments.get[String]("name")}"
  def title(arguments: Arguments): String =
    format(arguments)

  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val oldName = arguments.get[String]("dataset")
    val newName = arguments.get[String]("name")
    val ds = context.artifact(oldName).get
    context.output(newName, ds)
    context.delete(oldName)
  }

  def predictProvenance(arguments: Arguments) = 
    Some( (Seq(arguments.get[String]("dataset")), 
           Seq(arguments.get[String]("dataset"), arguments.get[String]("name"))) )


}

