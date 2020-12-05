package info.vizierdb.commands.vizual

import info.vizierdb.commands._

object RenameDataset extends Command
{
  def name: String = "Rename Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    StringParameter(id = "name", name = "New Dataset Name")
  )
  def format(arguments: Arguments): String = 
    s"RENAME DATASET ${arguments.get[String]("dataset")} TO ${arguments.get[String]("name")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val oldName = arguments.get[String]("dataset")
    val newName = arguments.get[String]("name")
    val ds = context.artifact(oldName).get
    context.output(newName, ds)
    context.delete(oldName)
  }
}