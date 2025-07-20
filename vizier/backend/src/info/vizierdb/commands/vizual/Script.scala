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

import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.spark.vizual
import info.vizierdb.spark.SparkSchema
import org.apache.spark.sql.types.{ StructType, StringType } 
import info.vizierdb.VizierException
import info.vizierdb.spark.SparkPrimitive

object Script extends VizualCommand
{
  val commands = Seq[(String, String, (Arguments => vizual.VizualCommand))](
    ("Delete Column", "delete_column", { args => 
        vizual.DeleteColumn(args.get[Int]("column")) 
    }),

    ("Delete Row", "delete_row", { args => 
        vizual.DeleteRow(args.get[String]("row").toLong)
    }),

    ("Insert Column", "insert_column", { args => 
      vizual.InsertColumn(
        position = args.getOpt[Int]("position"),
        name = args.get[String]("name"),
        dataType = args.getOpt[String]("dataType").map { SparkSchema.decodeType(_) }
      )
    }),

    ("Insert Row", "insert_row", { args => 
      // val values = args.getRecord("values")
      vizual.InsertRow(
        position = Some(args.get[Int]("position")),
        values = None
      )
    }),

    ("Move Column", "move_column", { args => 
      vizual.MoveColumn(
        column = args.get[Int]("column"),
        position = args.get[Int]("position")
      )
    }),

    ("Move Row", "move_row", { args => 
      vizual.MoveRow(
        row = args.get[String]("row"),
        position = args.get[Int]("position")
      )
    }),

    ("Rename Column", "rename_column", { args => 
      vizual.RenameColumn(
        column = args.get[Int]("column"),
        name = args.get[String]("name")
      )
    }),

    ("Sort", "sort", { args => 
      vizual.Sort(Seq(
        vizual.SortColumn(args.get[Int]("column"), "asc")
      ))
    }),

    ("Update", "update", { args => 
      val rowSelection = args.getOpt[Int]("row") match {
        case Some(row) => vizual.RowsById(Set(row.toString))
        case None => vizual.AllRows()
      }
      vizual.UpdateCell(
        column = args.get[Int]("column"),
        row = Some(rowSelection),
        value = Some(JsString(args.get[String]("name"))),
        comment = args.getOpt[String]("comment")
      )
    }),
  )
  val constructor = commands.map { cmd => cmd._2 -> cmd._3 }.toMap

  def name: String = "Script"
  override def hidden = true
  def vizualParameters: Seq[Parameter] = Seq(
    ListParameter(id = "script", name = "Script", components = Seq(
      EnumerableParameter(id = "command", name = "Command",
                          values = EnumerableValue.withNames(commands.map { cmd => cmd._1 -> cmd._2 }:_*)),
      ColIdParameter(id = "column", name = "Column", required = false),
      RowIdParameter(id = "row", name = "Row", required = false),
      IntParameter(id = "position", name = "Position", required = false),
      StringParameter(id = "name", name = "Name/Value", required = false),
      StringParameter(id = "comment", name = "Comment", required = false),
      TemplateParameters.DATATYPE("dataType", required = false)
    ))
  )
  def format(arguments: Arguments): String = 
    s"DELETE COLUMN ${arguments.get[Int]("column")} FROM ${arguments.get[String]("dataset")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    arguments.getList("script")
             .map { args => constructor(args.get[String]("command"))(args) }


  def encode(script: Seq[vizual.VizualCommand]): Seq[Map[String, Any]] =
    script.map { 
      case vizual.DeleteColumn(column) => 
        Map("command" -> "delete_column", "column" -> column)
      case vizual.DeleteRow(row) => 
        Map("command" -> "delete_row", "row" -> row)
      case vizual.InsertColumn(position, name, dataType) => 
        Map("command" -> "insert_column", "position" -> position, "name" -> name, "dataType" -> SparkSchema.encodeType(dataType.getOrElse { StringType }))
      case vizual.InsertRow(position, values) => 
        Map("command" -> "insert_row", "position" -> position, "values" -> None) 
      case vizual.MoveColumn(column, position) => 
        Map("command" -> "move_column", "column" -> column, "position" -> position)
      case vizual.MoveRow(row, position) => 
        Map("command" -> "move_row", "row" -> row, "position" -> position)
      case vizual.RenameColumn(column, name) => 
        Map("command" -> "rename_column", "column" -> column, "name" -> name)
      case vizual.Sort(Seq(vizual.SortColumn(column, asc))) => 
        Map("command" -> "sort", "column" -> column)
      case vizual.UpdateCell(column, Some(vizual.RowsById(rows)), value, comment) if (rows.size == 1) => 
        Map("command" -> "update", "column" -> column, "row" -> rows.head.toLong, "name" -> value, "comment" -> comment)
      case vizual.UpdateCell(column, (Some(vizual.AllRows()) | None), value, comment) => 
        Map("command" -> "update", "column" -> column, "name" -> value, "comment" -> comment)
      case vizual.UpdateCell(column, _, value, comment) => 
        throw new VizierException(s"Unsupported in scripts (for now): update cell on multiple rows")
      case vizual.Sort(Seq(order)) => 
        throw new VizierException(s"Unsupported in scripts (for now): sort with multiple columns or descending order")
      case cmd:vizual.FilterColumns => 
        throw new VizierException(s"Unsupported in scripts: $cmd")
    }
}

