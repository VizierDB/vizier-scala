package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

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


}