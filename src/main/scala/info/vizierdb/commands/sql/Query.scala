package info.vizierdb.commands.sql

import play.api.libs.json._
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import org.mimirdb.api.request.CreateViewRequest
import com.typesafe.scalalogging.LazyLogging

object Query extends Command
  with LazyLogging
{
  def name: String = "SQL Query"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = "source", language = "sql", name = "SQL Code"),
    StringParameter(id = "output_dataset", name = "Output Dataset", required = false)
  )
  def format(arguments: Arguments): String = 
    s"${arguments.pretty("source")} TO ${arguments.pretty("output_dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val scope = context.allDatasets.mapValues { _.nameInBackend }
    val datasetName = arguments.getOpt[String]("output_dataset").getOrElse { "temporary_dataset" }
    val (dsName, dsId) = context.outputDataset(datasetName)
    val query = arguments.get[String]("source")

    logger.debug(s"$scope : $query")

    CreateViewRequest(
      input = scope, 
      functions = None,
      query = query, 
      resultName = Some(dsName),
      properties = None
    ).handle

    context.displayDataset(datasetName)
  }
}