package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object MoveColumn extends VizualCommand
{
  def name: String = "Move Column"
  def vizualParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = "column", name = "Column"),
    IntParameter(id = "position", name = "Position")
  )
  def format(arguments: Arguments): String = 
    s"MOVE COLUMN ${arguments.pretty("column")} IN DATASET ${arguments.get[String]("dataset")} TO POSITION ${arguments.get[Int]("position")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.MoveColumn(
        column = arguments.get[Int]("column"),
        position = arguments.get[Int]("position")
      )
    )
}