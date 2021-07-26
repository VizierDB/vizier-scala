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

import play.api.libs.json.Json
import org.mimirdb.api.request.{ UnloadRequest, UnloadResponse }
import org.mimirdb.api.{ Tuple => MimirTuple }
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import java.io.File
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.viztrails.ProvenancePrediction

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
  def title(arguments: Arguments): String = 
    s"Unload ${arguments.pretty("dataset")}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String]("dataset")
    val dataset = context.dataset(datasetName)
                         .getOrElse{ 
                           context.error(s"Dataset $datasetName does not exist"); return
                         }
    val format = arguments.get[String]("unloadFormat")

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
      backendOption = 
        arguments.getList("unloadOptions")
                 .map { option => MimirTuple(option.get[String]("unloadOptionKey"),
                                             option.get[String]("unloadOptionValue")) }
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
    ProvenancePrediction
      .definitelyReads(arguments.get[String]("dataset"))
      .definitelyWrites("file_export")
      .andNothingElse
}

