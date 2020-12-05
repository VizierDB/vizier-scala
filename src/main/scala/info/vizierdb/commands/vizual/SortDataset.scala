package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual

object SortDataset extends VizualCommand
{
  def name: String = "Sort Dataset"
  def vizualParameters: Seq[Parameter] = Seq(
    ListParameter(id = "columns", name = "Columns", components = Seq(
      ColIdParameter(id = "columns_column", name = "Column"),
      EnumerableParameter(id = "columns_order", name = "Order", values = EnumerableValue.withNames(
        "A -> Z" -> "asc",
        "Z -> A" -> "desc",
      ), default = Some(0))
    ))
  )
  def format(arguments: Arguments): String = 
    s"SORT DATASET ${arguments.get[String]("dataset")} BY ${arguments.getList("columns")
            .map { col => 
              s"(${arguments.get[String]("columns_column")} ${arguments.get[String]("columns_order").toUpperCase})"
            }.mkString(", ")}"

  def script(arguments: Arguments, context: ExecutionContext) = 
    Seq(
      vizual.Sort(
        arguments.getList("columns")
                 .map { col => 
                  vizual.SortColumn(
                    column = arguments.get[Int]("columns_column"),
                    order = arguments.get[String]("columns_order")
                  )
                 }
      )
    )
}