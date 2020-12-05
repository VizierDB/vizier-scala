package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object RenameColumn extends VizualCommand
{
  def name: String = "Rename Column"
  def vizualParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "column", name = "Column"),
    StringParameter(id = "name", name = "New Column Name")
  )
  def format(arguments: Arguments): String = 
    s"RENAME COLUMN ${arguments.pretty("column")} IN ${arguments.get[String]("dataset")} TO ${arguments.get[String]("name")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.RenameColumn(
        column = arguments.get[Int]("column"),
        name = arguments.get[String]("name")
      )
    )
}