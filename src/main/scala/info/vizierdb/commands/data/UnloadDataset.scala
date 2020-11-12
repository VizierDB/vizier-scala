package info.vizierdb.commands.data

import play.api.libs.json.JsValue
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import com.typesafe.scalalogging.LazyLogging

object UnloadDataset extends Command
  with LazyLogging
{
  def name: String = "Unload Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = "dataset", name = "Dataset"),
    EnumerableParameter(id = "unloadFormat", name = "Unload Format", values = EnumerableValue.withNames(
      "CSV"          -> "csv", 
      "JSON"         -> "json", 
      "Google Sheet" -> "mimir.exec.spark.datasource.google.spreadsheet", 
      "XML"          -> "com.databricks.spark.xml", 
      "Excel"        -> "com.crealytics.spark.excel", 
      "JDBC Source"  -> "jdbc", 
      "Text"         -> "text", 
      "Parquet"      -> "parquet", 
      "ORC"          -> "orc"
    ), default = Some(0)),
    ListParameter(id = "unloadOptions", name = "Unload Options", components = Seq(
      StringParameter(id = "unloadOptionKey", name = "Option Key"),
      StringParameter(id = "unloadOptionValue", name = "Option Value")
    ), required = false)
  )
  def format(arguments: Arguments): String = 
    s"UNLOAD ${arguments.pretty("dataset")} TO ${arguments.pretty("unloadFormat")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val dataset = context.dataset(datasetName)
                         .getOrElse{ 
                           context.error(s"Dataset $datasetName does not exist"); return
                         }
    val format = arguments.get[String]("unloadFormat")
    val (file, identifier) = Filestore.freshFile
    val response: UnloadResponse = UnloadRequest(
      input = dataset,
      file = file.toString(),
      format = format,
      backendOption = 
        arguments.getList("unloadOptions")
                 .map { option => MimirTuple(option.get[String]("unloadOptionKey"),
                                             option.get[String]("unloadOptionValue")) }
    ).handle

    logger.debug(response.toString())

    context.message("text/html", 
      response
        .outputFiles
        .map { f => new File(f) }
        .map { f =>
          s"<div><a href='${VizierAPI.filePath(identifier)}/${f.getName}' download='${datasetName}${f.getName}'>"+
            s"Download ${f.getName}</a></div>"
        }
        .mkString("\n")
    )

  }
}