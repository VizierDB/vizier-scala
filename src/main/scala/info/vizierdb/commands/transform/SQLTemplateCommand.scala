package info.vizierdb.commands.transform

import info.vizierdb.commands._
import info.vizierdb.catalog.Artifact
import info.vizierdb.types.ArtifactType
import org.mimirdb.api.request.CreateViewRequest
import info.vizierdb.commands.sql.Query
import com.typesafe.scalalogging.LazyLogging

trait SQLTemplateCommand 
  extends Command
  with LazyLogging
{
  val PARAM_OUTPUT_DATASET = "output_dataset"

  val DEFAULT_DS_NAME = Query.TEMPORARY_DATASET
  
  def templateParameters: Seq[Parameter]
  def parameters = 
    templateParameters :+
      StringParameter(id = PARAM_OUTPUT_DATASET, name = "Output Dataset", required = false)

  def query(
    arguments: Arguments, 
    context: ExecutionContext
  ): (Map[String, Artifact], String)

  def process(
    arguments: Arguments, 
    context: ExecutionContext
  ) {
    // Query has to come before we allocate the output dataset name
    val (deps, sql)  = query(arguments, context)
    val outputDatasetName = arguments.getOpt[String](PARAM_OUTPUT_DATASET)
                                     .getOrElse { DEFAULT_DS_NAME }
    val (dsName, dsId) = context.outputDataset(outputDatasetName)
    logger.debug(s"$sql")

    try { 
      logger.trace("Creating view")
      val response = CreateViewRequest(
        input = deps.mapValues { _.nameInBackend },
        functions = None,
        query = sql, 
        resultName = Some(dsName),
        properties = None
      ).handle

      logger.trace("Rendering dataset summary")
      context.displayDataset(outputDatasetName)
    } catch { 
      case e: org.mimirdb.api.FormattedError => 
        context.error(e.response.errorMessage)
    }
  }
}
