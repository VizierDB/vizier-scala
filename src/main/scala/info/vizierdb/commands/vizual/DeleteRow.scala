package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object DeleteRow extends VizualCommand
{
  def name: String = "Delete Row"
  def vizualParameters: Seq[Parameter] = Seq(
    RowIdParameter(id = "row", name = "Row")
  )
  def format(arguments: Arguments): String = 
    s"DELETE ROW ${arguments.get[String]("row")} FROM ${arguments.get[String]("dataset")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.DeleteRow(arguments.get[String]("row").toLong)
    )
}