package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object DeleteColumn extends VizualCommand
{
  def name: String = "Delete Column"
  def vizualParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "column", name = "Column")
  )
  def format(arguments: Arguments): String = 
    s"DELETE COLUMN ${arguments.get[Int]("column")} FROM ${arguments.get[String]("dataset")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.DeleteColumn(arguments.get[Int]("column"))
    )
}