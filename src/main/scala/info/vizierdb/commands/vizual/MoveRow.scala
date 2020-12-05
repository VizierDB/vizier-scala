package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object MoveRow extends VizualCommand
{
  def name: String = "Move Row"
  def vizualParameters: Seq[Parameter] = Seq(
    RowIdParameter(id = "row", name = "Row"),
    IntParameter(id = "position", name = "Position")
  )
  def format(arguments: Arguments): String = 
    s"MOVE ROW ${arguments.pretty("row")} IN DATASET ${arguments.get[String]("dataset")} TO POSITION ${arguments.get[Int]("position")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.MoveRow(
        row = arguments.get[String]("row"),
        position = arguments.get[Int]("position")
      )
    )
}