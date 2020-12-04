package info.vizierdb.commands.vizual

import info.vizierdb.commands._

object DropDataset extends Command
{
  def name: String = "Drop Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
  )
  def format(arguments: Arguments): String = 
    s"DROP DATASET ${arguments.get[String]("dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    context.delete(datasetName)
  }
}