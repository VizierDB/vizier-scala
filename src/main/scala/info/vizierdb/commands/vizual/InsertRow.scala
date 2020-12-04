package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object InsertRow extends VizualCommand
{
  def name: String = "Insert Row"
  def vizualParameters: Seq[Parameter] = Seq(
    IntParameter(id = "position", name = "Position", required = false),
  )
  def format(arguments: Arguments): String = 
    s"INSERT ROW INTO DATASET ${arguments.pretty("dataset")} AT POSITION ${arguments.get[Int]("position")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.InsertRow(
        position = arguments.get[Int]("position"),
      )
    )
}