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
import play.api.libs.json.JsValue
import info.vizierdb.commands._
import org.mimirdb.api.request.LoadRequest
import info.vizierdb.VizierException
import info.vizierdb.types._
import org.mimirdb.api.{ Tuple => MimirTuple }
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.spark.Schema
import org.apache.spark.sql.types.StructField
import org.mimirdb.api.FormattedError
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.catalog.PublishedArtifact
import org.mimirdb.api.request.CreateViewRequest
import play.api.libs.json.JsObject

object LoadDataset
  extends Command
  with LazyLogging
{

  val PARAM_FILE = "file"
  val PARAM_NAME = "name"
  val PARAM_FORMAT = "loadFormat"
  val PARAM_GUESS_TYPES = "loadInferTypes"
  val PARAM_HEADERS = "loadDetectHeaders"
  val PARAM_ANNOTATE_ERRORS = "loadDataSourceErrors"
  val PARAM_OPTIONS = "loadOptions"
  val PARAM_OPTION_KEY = "loadOptionKey"
  val PARAM_OPTION_VALUE = "loadOptionValue"

  def name: String = "Load Dataset"
  def parameters = Seq[Parameter](
    FileParameter(name = "Source File", id = PARAM_FILE),
    StringParameter(name = "Dataset Name", id = PARAM_NAME),
    EnumerableParameter(name = "Load Format", id = PARAM_FORMAT, values = EnumerableValue.withNames(
      "CSV"               -> "csv",
      "JSON"              -> "json",
      "PDF"               -> "mimir.exec.spark.datasource.pdf",
      "Google Sheet"      -> "mimir.exec.spark.datasource.google.spreadsheet",
      "XML"               -> "com.databricks.spark.xml",
      "Excel"             -> "com.crealytics.spark.excel",
      "JDBC Source"       -> "jdbc",
      "Text"              -> "text",
      "Parquet"           -> "parquet",
      "ORC"               -> "orc",
      "Locally Published" -> "publish_local",
    ), default = Some(0)),
    TemplateParameters.SCHEMA,
    BooleanParameter(name = "Guess Types", id = PARAM_GUESS_TYPES, default = Some(true)),
    BooleanParameter(name = "File Has Headers", id = PARAM_HEADERS, default = Some(true)),
    BooleanParameter(name = "Annotate Load Errors", id = PARAM_ANNOTATE_ERRORS, default = Some(true)),
    ListParameter(name = "Load Options", id = PARAM_OPTIONS, required = false, components = Seq(
      StringParameter(name = "Option Key", id  = PARAM_OPTION_KEY),
      StringParameter(name = "Option Value", id  = PARAM_OPTION_VALUE),
    ))
  )
  def format(arguments: Arguments): String = 
    s"LOAD DATASET ${arguments.pretty(PARAM_NAME)} AS ${arguments.pretty(PARAM_FORMAT)} FROM ${arguments.pretty(PARAM_FILE)}"
  def title(arguments: Arguments): String = 
    s"Load ${arguments.pretty(PARAM_NAME)}"
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    val datasetName = arguments.get[String](PARAM_NAME).toLowerCase()
    val format = arguments.get[String](PARAM_FORMAT)

    if(context.artifactExists(datasetName))
    {
      throw new VizierException("Dataset $name already exists.")
    }
    val file = arguments.get[FileArgument](PARAM_FILE)
    val (dsName, dsId) = context.outputDataset(datasetName)
    logger.trace(arguments.yaml())

    // Special-Case publish local
    if(format.equals("publish_local") || file.url.map { _.startsWith("vizier://") }.getOrElse(false)){
      val url = file.url.map { _.replace("vizier://", "http://").replace("viziers://", "https://")}
                        .getOrElse{
                          context.error("No URL provided")
                          return
                        }
      val exportName = 
        if(url.startsWith("http://") || url.startsWith("https://")){
          PublishedArtifact.nameFromURL(url)
                           .getOrElse {
                             context.error(s"${file.url.get} is not a valid local dataset url")
                             return
                            }
        } else { url }
      val published: PublishedArtifact = 
        DB.readOnly { implicit s => 
          PublishedArtifact.getOption(exportName)
        }.getOrElse {
          context.error(s"No published dataset named ${exportName} exists")
          return
        }
      val source = DB.readOnly { implicit s => published.artifact }
      CreateViewRequest(
        input = Map("input" -> source.nameInBackend),
        functions = None,
        query = "SELECT * FROM input",
        resultName = Some(dsName),
        properties = None
      ).handle
      context.displayDataset(datasetName)
      return
    }

    val proposedSchema =
      arguments.getList("schema") match {
        case Seq() => None
        case x => Some(x.map { arg => 
          StructField(
            arg.get[String]("schema_column"),
            Schema.decodeType(arg.get[String]("schema_datatype"))
          )
        })
      }
    
    val (path, relative) = file.getPath(context.projectId)

    logger.debug(s"Source: $file")
    logger.debug(s"${if(relative){"RELATIVE"}else{"ABSOLUTE"}} PATH: $path")

    try {
      val result = LoadRequest(
        file = path,
        format = format,
        inferTypes = arguments.get[Boolean](PARAM_GUESS_TYPES),
        detectHeaders = arguments.get[Boolean](PARAM_HEADERS),
        humanReadableName = Some(file.filename.getOrElse { datasetName }),
        backendOption = arguments.getList(PARAM_OPTIONS)
                                 .map { option => MimirTuple(option.get[String](PARAM_OPTION_KEY),
                                                             option.get[String](PARAM_OPTION_VALUE)) },
        dependencies = None,
        resultName = Some(dsName),
        properties = None,
        proposedSchema = proposedSchema,
        urlIsRelativeToDataDir = Some(relative)
      ).handle

      /** 
       * Replace the proposed schema with the inferred/actual schema
       */
      context.updateArguments(
        PARAM_FILE -> DB.readOnly { implicit s:DBSession => file.withGuessedFilename(Some(context.projectId)) },
        "schema" -> result.schema.map { field =>
          Map(
            "schema_column" -> field.name,
            "schema_datatype" -> Schema.encodeType(field.dataType)
          )
        }
      )

      context.displayDataset(datasetName)
    } catch { 
      case error: FormattedError => 
        context.error(error.getMessage())
    }
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) = 
    ProvenancePrediction
      .definitelyWrites(arguments.get[String]("name"))
      .andNothingElse
}

