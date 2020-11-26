package info.vizierdb.commands.data

import play.api.libs.json.JsValue
import info.vizierdb.commands._
import org.mimirdb.api.request.LoadRequest
import info.vizierdb.VizierException
import info.vizierdb.types._
import org.mimirdb.api.{ Tuple => MimirTuple }
import com.typesafe.scalalogging.LazyLogging

object LoadDataset
  extends Command
  with LazyLogging
{
  def name: String = "Load Dataset"
  def parameters = Seq[Parameter](
    FileParameter(name = "Source File", id = "file"),
    StringParameter(name = "Dataset Name", id = "name"),
    EnumerableParameter(name = "Load Format", id = "loadFormat", values = EnumerableValue.withNames(
      "CSV"          -> "csv",
      "JSON"         -> "json",
      "PDF"          -> "mimir.exec.spark.datasource.pdf",
      "Google Sheet" -> "mimir.exec.spark.datasource.google.spreadsheet",
      "XML"          -> "com.databricks.spark.xml",
      "Excel"        -> "com.crealytics.spark.excel",
      "JDBC Source"  -> "jdbc",
      "Text"         -> "text",
      "Parquet"      -> "parquet",
      "ORC"          -> "orc",
    ), default = Some(0)),
    BooleanParameter(name = "Guess Types", id = "loadInferTypes", default = Some(false)),
    BooleanParameter(name = "File Has Headers", id = "loadDetectHeaders", default = Some(false)),
    BooleanParameter(name = "Annotate Load Errors", id = "loadDataSourceErrors", default = Some(false)),
    ListParameter(name = "Load Options", id = "loadOptions", required = false, components = Seq(
      StringParameter(name = "Option Key", id  = "loadOptionKey"),
      StringParameter(name = "Option Value", id  = "loadOptionValue"),
    ))
  )
  def format(arguments: Arguments): String = 
    s"LOAD DATASET ${arguments.pretty("name")} AS ${arguments.pretty("loadFormat")} FROM ${arguments.pretty("file")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("name").toLowerCase()
    if(context.artifactExists(datasetName))
    {
      throw new VizierException("Dataset $name already exists.")
    }
    val file = arguments.get[FileArgument]("file")
    val (dsName, dsId) = context.outputDataset(datasetName)
    logger.trace(arguments.yaml())
    val result = LoadRequest(
      file = file.getPath(context.projectId),
      format = arguments.get[String]("loadFormat"),
      inferTypes = arguments.get[Boolean]("loadInferTypes"),
      detectHeaders = arguments.get[Boolean]("loadDetectHeaders"),
      humanReadableName = Some(file.filename.getOrElse { datasetName }),
      backendOption = arguments.getList("loadOptions")
                               .map { option => MimirTuple(option.get[String]("loadOptionKey"),
                                                           option.get[String]("loadOptionValue")) },
      dependencies = None,
      resultName = Some(dsName),
      properties = None,
      proposedSchema = None
    ).handle
    context.displayDataset(datasetName)
  }
}