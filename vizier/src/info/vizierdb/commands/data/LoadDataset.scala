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
import info.vizierdb.commands._
import info.vizierdb.VizierException
import info.vizierdb.types._
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.spark.SparkSchema
import org.apache.spark.sql.types.StructField
import java.io.FileNotFoundException
import java.io.IOException
import info.vizierdb.api.FormattedError
import info.vizierdb.filestore.Staging
import info.vizierdb.spark.LoadConstructor

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
      "CSV"          -> DatasetFormat.CSV,
      "JSON"         -> DatasetFormat.JSON,
      "PDF"          -> DatasetFormat.PDF,
      "Google Sheet" -> DatasetFormat.GSheet,
      "XML"          -> DatasetFormat.XML,
      "Excel"        -> DatasetFormat.Excel,
      "JDBC Source"  -> DatasetFormat.JDBC,
      "Text"         -> DatasetFormat.Text,
      "Parquet"      -> DatasetFormat.Parquet,
      "ORC"          -> DatasetFormat.ORC,
    ), default = Some(0)),
    TemplateParameters.SCHEMA,
    BooleanParameter(name = "Guess Types", id = PARAM_GUESS_TYPES, default = Some(false)),
    BooleanParameter(name = "File Has Headers", id = PARAM_HEADERS, default = Some(false)),
    BooleanParameter(name = "Annotate Load Errors", id = PARAM_ANNOTATE_ERRORS, default = Some(false)),
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
    if(context.artifactExists(datasetName))
    {
      throw new VizierException("Dataset $name already exists.")
    }
    val file = arguments.get[FileArgument](PARAM_FILE)

    logger.trace(arguments.yaml())

    val proposedSchema =
      arguments.getList("schema").map { arg => 
          StructField(
            arg.get[String]("schema_column"),
            SparkSchema.decodeType(arg.get[String]("schema_datatype"))
          )
        }
    
    val (path, relative) = file.getPath(context.projectId)

    logger.debug(s"Source: $file")
    logger.debug(s"${if(relative){"RELATIVE"}else{"ABSOLUTE"}} PATH: $path")


    var actualFile = file
    var storageFormat = arguments.get[String](PARAM_FORMAT)
    var finalSparkOptions = 
      defaultLoadOptions(
        storageFormat, 
        arguments.get[Boolean](PARAM_HEADERS)
      ) ++ arguments.getList(PARAM_OPTIONS)
                    .map { option => 
                      option.get[String](PARAM_OPTION_KEY) ->
                        option.get[String](PARAM_OPTION_VALUE)
                    }
    try {

      val mimirOptions = scala.collection.mutable.Map[String, JsValue]()

      // Do some pre-processing / default configuration for specific formats
      //  to make the API a little friendlier.
      storageFormat match {

        // The Google Sheets loader expects to see only the last two path components of 
        // the sheet URL.  Rewrite full URLs if the user provides the full path.
        case DatasetFormat.GSheet => {
          actualFile = 
            FileArgument(
              url = Some(actualFile.url.get.split("/").reverse.take(2).reverse.mkString("/"))
            )
        }
        
        // For everything else do nothing
        case _ => {}
      }

      if(file.needsStaging) {
        val url = actualFile.getPath(context.projectId, 
                                     noRelativePaths = true)._1
        // Preserve the original URL and configurations in the mimirOptions
        mimirOptions("preStagedUrl") = JsString(url)
        mimirOptions("preStagedSparkOptions") = Json.toJson(finalSparkOptions)
        mimirOptions("preStagedFormat") = JsString(storageFormat)
        val stagedConfig  = Staging.stage(url = url, 
                                          sparkOptions = finalSparkOptions, 
                                          format = storageFormat, 
                                          projectId = context.projectId, 
                                          allocateArtifactId = () => {
                                            context.outputFile(datasetName, MIME.RAW)
                                                   .id
                                          })
        actualFile        = stagedConfig._1
        finalSparkOptions = stagedConfig._2
        storageFormat     = stagedConfig._3
      }

      var loadConstructor = LoadConstructor(
        url = actualFile,
        format = storageFormat,
        sparkOptions = finalSparkOptions,
        contextText = Some(datasetName),
        proposedSchema = Some(
                          arguments.getList(TemplateParameters.PARAM_SCHEMA)
                                   .map { col =>
                                      StructField(
                                        col.get[String](TemplateParameters.PARAM_SCHEMA_COLUMN),
                                        SparkSchema.decodeType(
                                          col.get[String](TemplateParameters.PARAM_SCHEMA_TYPE)
                                        )
                                      )
                                   }
                        ),
        projectId = context.projectId
      )
      if(arguments.get[Boolean](PARAM_GUESS_TYPES)){
        loadConstructor = loadConstructor.withInferredTypes
      }

      val dataframe = loadConstructor.construct(_ => throw new VizierException("Internal error; Load Constructor should not be chaining dataframes"))

      context.outputDataset(
        name = arguments.get[String](PARAM_NAME),
        constructor = loadConstructor,
        properties = Map.empty
      )

      /** 
       * Replace the proposed schema with the inferred/actual schema
       */
      context.updateArguments(
        PARAM_FILE -> DB.readOnly { implicit s => file.withGuessedFilename(Some(context.projectId)) },
        "schema" -> dataframe.schema.map { field =>
          Map(
            "schema_column" -> field.name,
            "schema_datatype" -> SparkSchema.encodeType(field.dataType)
          )
        }
      )
    } catch {
      case e: FileNotFoundException => 
        throw FormattedError(e, s"Can't Load URL [Not Found]: $file")
      case e: IOException => 
        throw FormattedError(e, s"Error Loading $file (${e.getMessage()}")
      case e: IllegalStateException if 
                storageFormat.equals(DatasetFormat.Excel) => 
        throw FormattedError(e, e.getMessage() + "\nThis can happen due to an upstream bug.  Try unchecking 'File has headers'")
    }

    context.displayDataset(datasetName)
  }

  def predictProvenance(arguments: Arguments) = 
    Some( (
      Seq.empty,
      Seq(arguments.get[String]("name"))
    ) )


  private val defaultLoadCSVOptions = Map(
    "ignoreLeadingWhiteSpace"-> "true",
    "ignoreTrailingWhiteSpace"-> "true"
  )
  
  private val defaultLoadGoogleSheetOptions = Map(
      "serviceAccountId" -> "vizier@api-project-378720062738.iam.gserviceaccount.com",
      "credentialPath" -> "test/data/api-project-378720062738-5923e0b6125f")
  
  def defaultLoadOptions(
    format: DatasetFormat.T, 
    header: Boolean
  ): Map[String,String] = 
  {
    format match {
      case DatasetFormat.CSV | DatasetFormat.Excel =>
        defaultLoadCSVOptions ++ Map("header" -> header.toString)
      case DatasetFormat.GSheet => defaultLoadGoogleSheetOptions
      case _ => Map()
    }
  }
}

