package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object UpdateCell extends VizualCommand
{
  def name: String = "Update Cell"
  def vizualParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "column", name = "Column"),
    StringParameter(id = "row", name = "Row (optional)", required = false),
    StringParameter(id = "value", name = "Value", required = false)
  )
  def format(arguments: Arguments): String = 
    s"UPDATE ${arguments.get[String]("dataset")}[${arguments.pretty("column")}${arguments.getOpt[String]("row").map { ":"+_ }.getOrElse{""}}] UPDATE ${arguments.pretty("value")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
  {
    val rowSelection = arguments.getOpt[String]("row") match {
      case Some(row) => vizual.RowsById(Set(row))
      case None => vizual.AllRows()
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