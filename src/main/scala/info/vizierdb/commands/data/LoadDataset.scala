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

import play.api.libs.json.JsValue
import info.vizierdb.commands._
import org.mimirdb.api.request.LoadRequest
import info.vizierdb.VizierException
import info.vizierdb.types._
import org.mimirdb.api.{ Tuple => MimirTuple }
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.spark.Schema
import org.apache.spark.sql.types.StructField

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
    TemplateParameters.SCHEMA,
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
  def title(arguments: Arguments): String = 
    s"Load ${arguments.pretty("name")}"
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


    val result = LoadRequest(
      file = path,
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
      proposedSchema = proposedSchema,
      urlIsRelativeToDataDir = Some(relative)
    ).handle

    /** 
     * Replace the proposed schema with the inferred/actual schema
     */
    context.updateArguments(
      "schema" -> result.schema.map { field =>
        Map(
          "schema_column" -> field.name,
          "schema_datatype" -> Schema.encodeType(field.dataType)
        )
      }
    )

    context.displayDataset(datasetName)
  }

  def predictProvenance(arguments: Arguments) = 
    Some( (
      Seq.empty,
      Seq(arguments.get[String]("name"))
    ) )
}

