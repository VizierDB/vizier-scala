package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.mimirdb.lenses.Lens
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.api.request.CreateLensRequest

trait LensCommand 
  extends Command
  with LazyLogging
{
  def lensParameters: Seq[Parameter]
  def lensArguments(arguments: Arguments, dataset: String, context: ExecutionContext): JsValue
  def formatUpdatedArguments(lensArgs: JsValue): Map[String, JsValue]
  def lens: String
  def lensFormat(arguments: Arguments): String

  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset")
  ) ++ lensParameters

  def format(arguments: Arguments): String =
    s"CREATE LENS ON ${arguments.get[String]("dataset")} ${lensFormat(arguments)}"

  def process(arguments: Arguments, context: ExecutionContext)
  {
    val datasetName = arguments.get[String]("dataset")

    logger.debug(s"${lens}($arguments) <- $datasetName")
    

    val input = context.dataset(datasetName)
                         .getOrElse { 
                            throw new IllegalArgumentException(s"No such dataset '$datasetName'")
                         }

    val mimirArguments = lensArguments(arguments, datasetName, context)

    val (output, _) = context.outputDataset(datasetName)

    logger.debug(s"${this.getClass().getName()} -> $input -> $output")

    val response = CreateLensRequest(
      input = input,
      `type` = lens,
      params = mimirArguments,
      resultName = Some(output),
      materialize = false,
      humanReadableName = Some(datasetName),
      properties = None
    ).handle

    val updatedArgs = formatUpdatedArguments(response.config)
    if( !updatedArgs.isEmpty ){
      context.updateJsonArguments(updatedArgs.toSeq:_*)
    }

    context.message(s"Created Lens on  $datasetName")
  }
}