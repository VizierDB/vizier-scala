package info.vizierdb.commands.vizual

import info.vizierdb.commands._
import org.mimirdb.vizual
import org.mimirdb.api.request.VizualRequest
import com.typesafe.scalalogging.LazyLogging

trait VizualCommand 
  extends Command
  with LazyLogging
{

  def vizualParameters: Seq[Parameter]
  def script(arguments: Arguments, context: ExecutionContext): Seq[vizual.Command]

  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset")
  ) ++ vizualParameters


  def process(arguments: Arguments, context: ExecutionContext)
  {
    val datasetName = arguments.get[String]("dataset")

    logger.debug(s"${this.getClass().getName()}($arguments)")

    val input = context.dataset(datasetName)
                         .getOrElse { 
                            throw new IllegalArgumentException(s"No such dataset '$datasetName'")
                         }

    val (output, _) = context.outputDataset(datasetName)

    logger.debug(s"${this.getClass().getName()} -> $input -> $output")

    VizualRequest(
      input = input,
      script = script(arguments, context),
      resultName = Some(output),
      compile = Some(false)
    ).handle

    context.message(s"Updated $datasetName")
  }

}