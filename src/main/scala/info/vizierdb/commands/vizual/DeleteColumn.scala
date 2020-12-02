package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.api.request.VizualRequest
import org.mimirdb.vizual
import org.mimirdb.api.MimirAPI

object DeleteColumn extends Command
  with LazyLogging
{
  def name: String = "Delete Column"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    ColIdParameter(id = "column", name = "Column")
  )
  def format(arguments: Arguments): String = 
    s"DELETE COLUMN ${arguments.get[Int]("column")} FROM ${arguments.get[String]("dataset")}"
  def process(arguments: Arguments, context: ExecutionContext)
  {
    val datasetName = arguments.get[String]("dataset")
    val column = arguments.get[Int]("column")

    val dataset = context.dataset(datasetName)
                         .getOrElse { 
                            throw new IllegalArgumentException(s"No such dataset '$datasetName'")
                         }

    val (output, _) = context.outputDataset(datasetName)

    VizualRequest(
      dataset,
      script = Seq(
        vizual.DeleteColumn(column)
      ),
      resultName = Some(output),
      compile = Some(false)
    ).handle
  }
}