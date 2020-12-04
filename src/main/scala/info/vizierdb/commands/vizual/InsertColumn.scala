package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object InsertColumn extends VizualCommand
{
  def name: String = "Insert Column"
  def vizualParameters: Seq[Parameter] = Seq(
    IntParameter(id = "position", name = "Position", required = false),
    StringParameter(id = "name", name = "Column Name")
  )
  def format(arguments: Arguments): String = 
    s"INSERT COLUMN ${arguments.get[String]("name")} INTO DATASET ${arguments.get[String]("dataset")}${arguments.getOpt[Int]("position").map {" AT POSITION "+_}.getOrElse{""}}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.InsertColumn(
        position = arguments.getOpt[Int]("position"),
        name = arguments.get[String]("name")
      )
    )
}