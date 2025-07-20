/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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

object SortDataset extends VizualCommand
{
  
  def name: String = "Sort Dataset"
  override def hidden = true
  def vizualParameters: Seq[Parameter] = Seq(
    ListParameter(id = "columns", name = "Columns", components = Seq(
      ColIdParameter(id = "columns_column", name = "Column"),
      EnumerableParameter(id = "columns_order", name = "Order", values = EnumerableValue.withNames(
        "A -> Z" -> "asc",
        "Z -> A" -> "desc",
      ), default = Some(0))
    ))
  )
  def format(arguments: Arguments): String = 
    s"SORT DATASET ${arguments.get[String]("dataset")} BY ${arguments.getList("columns")
            .map { col => 
              s"(${col.get[Int]("columns_column")} ${col.get[String]("columns_order").toUpperCase})"
            }.mkString(", ")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.Sort(
        arguments.getList("columns")
                 .map { col => 
                  vizual.SortColumn(
                    column = col.get[Int]("columns_column"),
                    order = col.get[String]("columns_order")
                  )
                 }
      )
    )
}

