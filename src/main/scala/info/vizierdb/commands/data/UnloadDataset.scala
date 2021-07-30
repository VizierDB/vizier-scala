/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.commands.data

import scalikejdbc.DB
import play.api.libs.json.Json
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.PublishedArtifact
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import com.typesafe.scalalogging.LazyLogging

object UnloadDataset extends Command
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_FORMAT = "unloadFormat"
  val PARAM_OPTIONS = "unloadOptions"
  val PARAM_OPTION_KEY = "unloadOptionKey"
  val PARAM_OPTION_VALUE = "unloadOptionValue"

  def name: String = "Unload Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = PARAM_DATASET, name = "Dataset"),
    EnumerableParameter(id = PARAM_FORMAT, name = "Unload Format", values = EnumerableValue.withNames(
      "CSV"             -> "csv", 
      "JSON"            -> "json", 
      "Google Sheet"    -> "mimir.exec.spark.datasource.google.spreadsheet", 
      "XML"             -> "com.databricks.spark.xml", 
      "Excel"           -> "com.crealytics.spark.excel", 
      "JDBC Source"     -> "jdbc", 
      "Text"            -> "text", 
      "Parquet"         -> "parquet", 
      "ORC"             -> "orc",
      "Publish Locally" -> "publish_local",
    ), default = Some(0)),
    ListParameter(id = PARAM_OPTIONS, name = "Unload Options", components = Seq(
      StringParameter(id = PARAM_OPTION_KEY, name = "Option Key"),
      StringParameter(id = PARAM_OPTION_VALUE, name = "Option Value")
    ), required = false)
  )
  def format(arguments: Arguments): String = 
    s"UNLOAD ${arguments.pretty(PARAM_DATASET)} TO ${arguments.pretty(PARAM_FORMAT)}"
  def title(arguments: Arguments): String = 
    s"Unload ${arguments.pretty(PARAM_DATASET)}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val format = arguments.get[String](PARAM_FORMAT)
    val optionList =
              arguments.getList(PARAM_OPTIONS)
                 .map { option => MimirTuple(option.get[String](PARAM_OPTION_KEY),
                                             option.get[String](PARAM_OPTION_VALUE)) }

    
    // Publish Local gets some special handling, since we're not creating an 
    // artifact.
    if(format.equals("publish_local")) {
      val artifact = context.artifact(datasetName)
                            .getOrElse{ 
                              context.error(s"Dataset $datasetName does not exist"); return
                            }

      val published:PublishedArtifact = DB.autoCommit { implicit s => 
        PublishedArtifact.make(
          artifact = artifact, 
          name = optionList.find { _.name.equalsIgnoreCase("name") }
                    .map { _.value },
          properties = Json.obj(),
          overwrite = true
        )
      }

      context.message(s"Access as ${published.url}")

      return
    }

    val dataset = context.dataset(datasetName)
                         .getOrElse{ 
                           context.error(s"Dataset $datasetName does not exist"); return
                         }


    val mimeTypeForFile = format match {
      case "mimir.exec.spark.datasource.google.spreadsheet" 
                                  => None
      case "jdbc"                 => None
      case "csv"                  => Some("text/csv")
      case "json"                 => Some("application/json")
      case "xml"                  => Some("application/xml")
      case "text"                 => Some("text/plain")
      case _                      => Some("application/octet-stream")
    }

    val artifactIfNeeded = 
      mimeTypeForFile.map { mimeType => 
        context.outputFile(
          name = "file_export", 
          properties = Json.obj(
            "filename" -> s"export_$datasetName"
          ),
          mimeType = mimeType
        )
      }

    val response: UnloadResponse = UnloadRequest(
      input = dataset,
      file = artifactIfNeeded.map { _.absoluteFile.toString }
                             .getOrElse { "unknown_file" },
      format = format,
      backendOption = optionList
    ).handle

    logger.debug(response.toString())

    artifactIfNeeded match {
      case Some(artifact) => 
        context.message("text/html", 
          response
            .outputFiles
            .map { f => new File(f) }
            .map { f =>
              s"<div><a href='${VizierAPI.urls.downloadFile(context.projectId, artifact.id, f.getName)}' download='${datasetName}${f.getName}'>"+
                s"Download ${f.getName}</a></div>"
            }
            .mkString("\n")
        )
      case None => 
         context.message("Export Successful")
    }

  }

  def predictProvenance(arguments: Arguments) = 
    Some( (
      Seq(arguments.get[String](PARAM_DATASET)),
      Seq("file_export")
    ) )
}

