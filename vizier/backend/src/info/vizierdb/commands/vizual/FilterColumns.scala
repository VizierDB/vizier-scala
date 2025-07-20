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

object FilterColumns extends VizualCommand
{
  def name: String = "Filter Columns"
  def vizualParameters: Seq[Parameter] = Seq(
    ListParameter(id = "columns", name = "Columns", components = Seq(
      ColIdParameter(id = "columns_column", name = "Column"),
      StringParameter(id = "columns_rename", name = "Rename as...", required = false)
    ))
  )
  def format(arguments: Arguments): String = 
    s"FILTER COLUMNS ${arguments.getList("columns")
                                .map { col => 
                                  col.get[Int]("columns_column") + 
                                    col.getOpt[String]("columns_rename")
                                       .map { " AS "+_ }
                                       .getOrElse { "" }
                                }
                                .mkString(", ")
                              } FROM ${arguments.get[String]("dataset")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
  {
    val schema = context.datasetSchema(arguments.get[String]("dataset")).get
    Seq(
      vizual.FilterColumns(
        columns = arguments.getList("columns")
                           .map { col => 
                              val position = col.get[Int]("columns_column")
                              vizual.FilteredColumn(
                                columns_column = position,
                                columns_name = col.getOpt[String]("columns_rename")
                                                  .getOrElse { schema(position).name }
                              )
                           }
      )
    )
  }
  override def hidden: Boolean = true


}

