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

import scalikejdbc._
import play.api.libs.json._
import org.apache.spark.sql.DataFrame
import info.vizierdb.commands._
import info.vizierdb.types._
import info.vizierdb.filestore.Filestore
import java.io.File
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.catalog.PublishedArtifact
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.Vizier
import java.net.URL
import info.vizierdb.VizierException

object UnloadDataset extends Command
  with LazyLogging
{
  val PARAM_DATASET = "dataset"
  val PARAM_FORMAT = "unloadFormat"
  val PARAM_OPTIONS = "unloadOptions"
  val PARAM_OPTIONS_KEY = "unloadOptionKey"
  val PARAM_OPTIONS_VALUE = "unloadOptionValue"
  val PARAM_URL = "url"

  val TEMPFILE_FORMATS = Set(
    DatasetFormat.CSV, 
    DatasetFormat.Text,
    DatasetFormat.JSON,
    DatasetFormat.XML,
    DatasetFormat.Excel,
  ) 

  def name: String = "Publish Dataset"
  def parameters: Seq[Parameter] = Seq(
    DatasetParameter(id = PARAM_DATASET, name = "Dataset"),
    EnumerableParameter(id = PARAM_FORMAT, name = "Unload Format", values = EnumerableValue.withNames(
      "CSV"               -> DatasetFormat.CSV, 
      "JSON"              -> DatasetFormat.JSON, 
      "Google Sheet"      -> DatasetFormat.GSheet, 
      "XML"               -> DatasetFormat.XML, 
      "Excel"             -> DatasetFormat.Excel, 
      "JDBC Source"       -> DatasetFormat.JDBC, 
      "Text"              -> DatasetFormat.Text, 
      "Parquet"           -> DatasetFormat.Parquet, 
      "ORC"               -> DatasetFormat.ORC,
      "Locally Published" -> "publish_local",
    ), default = Some(0)),
    StringParameter(id = PARAM_URL, name = "URL (optional)", required = false),
    ListParameter(id = PARAM_OPTIONS, name = "Unload Options", components = Seq(
      StringParameter(id = PARAM_OPTIONS_KEY, name = "Option Key"),
      StringParameter(id = PARAM_OPTIONS_VALUE, name = "Option Value")
    ), required = false),
  )
  def format(arguments: Arguments): String = 
    s"PUBLISH ${arguments.pretty(PARAM_DATASET)} TO ${arguments.pretty(PARAM_FORMAT)}"
  def title(arguments: Arguments): String = 
    s"Publish ${arguments.pretty(PARAM_DATASET)}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String](PARAM_DATASET)
    val format = arguments.get[String](PARAM_FORMAT)
    val optionList = 
      arguments.getList(PARAM_OPTIONS)
               .map { option => 
                  option.get[String](PARAM_OPTIONS_KEY) ->
                    option.get[String](PARAM_OPTIONS_VALUE)
               }

    // Publish Local gets some special handling, since we're not creating an 
    // artifact.
    if(format.equals("publish_local")) {
      val artifact = context.artifact(datasetName)
                            .getOrElse{ 
                              context.error(s"Dataset $datasetName does not exist"); return
                            }

      val published:PublishedArtifact = CatalogDB.withDB { implicit s => 
        PublishedArtifact.make(
          artifact = artifact, 
          name = optionList.find { _._1.equalsIgnoreCase("name") }
                    .map { _._2 },
          properties = Json.obj(),
          overwrite = true
        )
      }

      context.message(s"Access as ${published.url}")

      return
    }

    val dataset = context.artifact(datasetName)
                         .getOrElse{ 
                           context.error(s"Dataset $datasetName does not exist"); return
                         }

    val mimeTypeForFile = format match {
      case DatasetFormat.GSheet   => None
      case DatasetFormat.JDBC     => None
      case DatasetFormat.CSV      => Some("text/csv")
      case DatasetFormat.JSON     => Some("application/json")
      case DatasetFormat.XML      => Some("application/xml")
      case DatasetFormat.Text     => Some("text/plain")
      case _                      => Some("application/octet-stream")
    }

    val path = arguments.getOpt[String](PARAM_URL)
    val url: Option[URL] = path.map { Filestore.canonicalizePath(_) }

    val outputArtifactIfNeeded = 
      if(url.isDefined) { None }
      else {
        mimeTypeForFile.map { mimeType => 
          context.outputFilePlaceholder(
            name = "file_export", 
            properties = Json.obj(
              "filename" -> s"export_$datasetName"
            ),
            mimeType = mimeType
          )
        }
      }

    val useTempFile = ( 
      // We only want to use tempfiles for specific formats that dump out 
      // multiple files
      TEMPFILE_FORMATS(format)
        // .. but only if...
        && (
              // ... outputting to another artifact
             url.isEmpty 
              // ... or outputting to a local file (i.e., follow spark's safety 
              // conventions for s3a and other distributed filesystems) 
          || (url.get.getProtocol() == "file")
        )
    )

    if(useTempFile) { logger.trace("I think we should use a temporary file") }

    val df: DataFrame = 
      {
        var df = 
          CatalogDB.withDB { implicit s => dataset.dataframe }()
        // Tempfile formats need to be coalesced into a single partition
        // before they are dumped out.
        if(useTempFile){
          df = df.coalesce(1)
        }
        df
      }

    var writer = df.write.format(format)

    // Specific options for specific formats
    writer = format match {
      case DatasetFormat.Excel => {
        if(optionList.exists { _._1 == "header" }){
          writer.option("header", true)
        } else { writer }
      }
      case _ => writer

    }

    // User-provided options
    writer = optionList.foldLeft(writer) {
      case (writer, ("mode", mode)) => writer.mode(mode)
      case (writer, (option, value)) => writer.option(option, value)
    }


    // Generate the output target file
    val (file: URL, tempDir:Option[File]) = 
      if(useTempFile) {
        val tempDir = File.createTempFile("temp_", "."+format.split("\\.").last)
        tempDir.delete
        (new URL("file://"+tempDir.toString), Some(tempDir))
      } else if(url.isDefined) { 
        (
          url.get, 
          None
        )
      } else if(outputArtifactIfNeeded.isDefined) {
        (
          new URL("file://"+
            outputArtifactIfNeeded.get.absoluteFile.toString
          ),
          None
        )
      } else {
        throw new VizierException("Unsure where to unload the file")
      }

    // Some formats need special handling to reformat their URLs
    format match {
      case DatasetFormat.GSheet => {
        // The Sheets uploader doesn't take a full sheet URL, just the 
        // spreadsheetID/sheetID pair at the end of the URL.  Strip those
        // out and recreate the URL. 
        val sheetURLParts = file.getPath.split("\\/").reverse
        // NOTE THE `.reverse` above
        val sheetIdentifier = sheetURLParts(0) + "/" + sheetURLParts(1)
        writer.save(sheetIdentifier)
      }
      case _ => 
        logger.debug(s"Writing artifact $datasetName to $file")
        writer.save(file.toString)
    }

    // Copy the target file to the right place and clean up the temp dir
    if(tempDir.isDefined){
      val allFiles = tempDir.get.listFiles
      val dataFiles = allFiles.filter { _.getName.startsWith("part-") }
      assert(dataFiles.size == 1, s"Spark generated ${dataFiles.size} data files: ${dataFiles.mkString(", ")}")
      val artifact = 
        dataFiles.head.renameTo(
          url.map { u => new File(u.getPath) }.getOrElse {
            outputArtifactIfNeeded.get.absoluteFile
          }
        )
      for(f <- allFiles){
        if(f.exists){ f.delete }
      }
      tempDir.get.delete
    }

    outputArtifactIfNeeded match {
      case Some(artifact) => 
        context.message("text/html",
          s"<div><a href='${Vizier.urls.downloadFile(context.projectId, artifact.id)}' download='${datasetName}'>Download ${datasetName}</a></div>" 
        )
      case None => 
         context.message("Export Successful")
    }

  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyReads(arguments.get[String](PARAM_DATASET))
      .definitelyWrites("file_export")
      .andNothingElse


}

