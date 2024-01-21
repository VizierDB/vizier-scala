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
package info.vizierdb.commands.python

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.commands._
import info.vizierdb.types._
import info.vizierdb.filestore.Filestore
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.{ SparkPrimitive, LoadConstructor, InlineDataConstructor }
import info.vizierdb.spark.arrow.ArrowQuery
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.catalog.Artifact
import info.vizierdb.catalog.ArtifactRef
import info.vizierdb.spark.vizual.{ VizualCommand, VizualScriptConstructor }
import info.vizierdb.spark.caveats.QueryWithCaveats
import info.vizierdb.util.ExperimentalOptions
import info.vizierdb.viztrails.ProvenancePrediction
import info.vizierdb.catalog.CatalogDB
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.catalog.PythonVirtualEnvironment

object Python extends Command
  with LazyLogging
{
  val PROP_INPUT_PROVENANCE = "input_provenance"
  val PROP_OUTPUT_PROVENANCE = "output_provenance"
  val ARG_SOURCE = "source"
  val ARG_ENV = "environment"


  def name: String = "Python Script"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = ARG_SOURCE, language = "python", name = "Python Code"),
    EnvironmentParameter(id = ARG_ENV, language = LanguageType.PYTHON, name = "Environment", required = false)
  )
  def format(arguments: Arguments): String = 
    arguments.pretty(ARG_SOURCE)
  def title(arguments: Arguments): String =
  {
    val line1 = arguments.get[String](ARG_SOURCE).split("\n")(0)
    if(line1.startsWith("#")){
      line1.replaceFirst("^# *", "")
    } else {
      "Python Script"
    }
  }
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    logger.debug("Initializing...")
    val script = arguments.get[String](ARG_SOURCE)
    val env: PythonEnvironment = 
      arguments.getOpt[serialized.PythonEnvironmentSummary](ARG_ENV)
              .map { env => 
                // If the client has requested a specific environment...
                // ... start by checking whether it's an internal environment
                PythonEnvironment.INTERNAL_BY_ID.get(env.id)
                  .orElse { 
                    // .. if it isn't, see if it's a virtual environment
                    CatalogDB.withDBReadOnly { implicit session =>
                      PythonVirtualEnvironment.getByIdOption(env.id)
                    }.map { _.Environment }
                  }.getOrElse {
                    // ... and if it's not internal or virtual, then explode
                    context.error(s"Unknown Python Environment ${env.id} (${env.name})")
                    return
                  }
              }.getOrElse { 
                // If the client hasn't requested a specific environment,
                // then default to the system python
                SystemPython
              }
    val python = PythonProcess(env)

    python.send("script", 
      "script" -> JsString(script), 
      "artifacts" -> 
        JsObject(context.artifacts(registerInput = false).mapValues { artifact => 
          Json.obj(
            "type" -> artifact.t.toString(),
            "mimeType" -> artifact.mimeType,
            "artifactId" -> artifact.id
          )
        }),
      "projectId" -> JsNumber(context.projectId), 
      "cellId" -> JsString(context.executionIdentifier)
    )

    val ret = python.monitor { event => 
      logger.debug(s"STDIN: $event")

      // Many of the events handled by this handler are for interacting with artifacts
      // Every such event follows the same basic pattern: 
      //   1. Look up artifact by name
      //   2. Trigger error if it doesn't exist
      // The following two internal methods abstract this pattern out, with an additional
      // simplification based on the fact that nearly all artifact names appear in the 
      // event field 'name'.
      // If the artifact exists, the provided handler method will be invoked.  If it
      // doesn't the python process will be killed and an error will be logged.
      def withArtifact(handler: Artifact => Unit) =
        withNamedArtifact((event\"name").as[String]) { handler(_) }
      def withNamedArtifact(name: String)(handler: Artifact => Unit) =
        context.artifact( name ) match {
          case None => 
            val name = (event\"name").as[String]
            context.error(s"No such artifact '$name'")
            python.kill()
          case Some(artifact) => 
            handler(artifact)
        }
      try { 
        (event\"event").as[String] match {
          case "message" => 
            (event\"stream").as[String] match {  
              case "stdout" => 
                logger.trace(s"Python STDOUT: ${event\ "content"}")
                context.message( 
                  // each message object already gets a free newline, so trim here
                  content = (event\"content").as[String].trim(),
                  mimeType = (event\"mimeType").getOrElse { JsString(MIME.TEXT) }
                                              .as[String]
                )
              // each message object already gets a free newline, so trim here
              case "stderr" => {
                logger.warn( (event\"content").as[String].trim() )
                context.error( (event\"content").as[String].trim() )
              }
              case x => context.error(s"Received message on unknown stream '$x'")
            }
          case "get_dataset" => 
            withArtifact { artifact => 
              python.send("dataset",
                "data" -> Json.toJson(
                  CatalogDB.withDB { implicit s => 
                    artifact.datasetData(includeCaveats = true)
                  }()

                ),
                "artifactId" -> JsNumber(artifact.id)
              )
            }
          case "get_blob" => 
            withArtifact { artifact => 
              python.send("blob",
                "data" -> JsString(artifact.string),
                "artifactId" -> JsNumber(artifact.id)
              )
            }
          case "get_parameter" => 
            withArtifact { artifact => 
              val json = artifact.json
              python.send("parameter",
                "data" -> (json \ "value").asOpt[JsValue].getOrElse { JsNull },
                "dataType" -> (json \ "dataType").asOpt[JsValue].getOrElse { JsNull },
                "artifactId" -> JsNumber(artifact.id)
              )
            } 
          case "get_file" => 
            withArtifact { artifact => 
              python.send("file",
                "path" -> JsString(artifact.absoluteFile.toString),
                "artifactId" -> JsNumber(artifact.id),
                "url" -> JsString(artifact.url.toString),
                "properties" -> artifact.json
              )
            }
          case "save_dataset" =>
            {
              val ds = (event\"dataset")
              val artifact = context.outputDataset( 
                (event\"name").as[String],
                InlineDataConstructor(
                  schema = (ds\"schema").as[Seq[StructField]],
                  data = (ds\"data").as[Seq[Seq[JsValue]]],
                ),
                properties = (ds\"properties").as[Map[String,JsValue]],
              )

              python.send("datasetId",
                "artifactId" -> JsNumber(artifact.id)
              )
            }
          case "vizual_script" => 
            def handler(inputId: Option[Identifier], inputSch: Option[Seq[StructField]]): Unit = {
              val output = (event\"output").as[String]
              val script = (event\"script").as[Seq[VizualCommand]]

              val artifact = context.outputDataset( 
                name = output,
                constructor = VizualScriptConstructor(
                   script = script,
                   input = inputId,
                   VizualScriptConstructor.getSchema(inputSch, script)
                )
              )
              logger.trace(s"Visual being used to create dataset: $output (artifact ${artifact.id})")

              python.send("datasetId",
                "artifactId" -> JsNumber(artifact.id)
              )
            }
            ((event\"name").asOpt[String]:Option[String]) match {
              case None => 
                handler(None, None)
              case Some(ds) => 
                withNamedArtifact(ds) { existingDs => 
                  assert(
                    (event\"identifier").as[Long] == existingDs.id,
                    s"Vizual script for ${(event\"output")} with out-of-sync identifier (Python has ${(event\"identifier")}; Spark has ${existingDs.id})"
                  )
                  handler(Some(existingDs.id), Some(existingDs.datasetSchema)) 
                }
            }
          case "vizier_script" => 
            {
              val inputs = (event\"inputs").as[Map[String, String]]
              val outputs = (event\"outputs").as[Map[String, String]]
              val quiet = (event\"quiet").asOpt[Boolean].getOrElse(true)
              context.runScript(
                name = (event\"script").as[String],
                inputs = inputs,
                outputs = outputs,
              )
              python.send("script_datasets",
                "outputs" -> JsObject(
                  outputs.map { case (scriptName, myName) =>
                    val artifact = context.artifact(myName, registerInput = false).get
                    myName -> Json.obj(
                      "type" -> artifact.t.toString(),
                      "mimeType" -> artifact.mimeType,
                      "artifactId" -> artifact.id
                    )
                  }.toMap
                )
              )
            }
          case "create_dataset" => 
            {
              val artifact = 
                context.outputDataset( 
                  (event\"name").as[String],
                  LoadConstructor(
                    url = FileArgument( fileid = Some((event \ "file").as[String].toLong) ),
                    format = (event\"format").asOpt[String].getOrElse("parquet"),
                    sparkOptions = Map.empty,
                    contextText = Some( (event \ "name").as[String] ),
                    proposedSchema = None,
                    urlIsRelativeToDataDir = None,
                    projectId = context.projectId
                  )
                )

              python.send("datasetId",
                "artifactId" -> JsNumber(artifact.id)
              )
            }
          case "save_artifact" =>
            {
              val t = ArtifactType.withName( (event\"artifactType").as[String] )
              val artifact = context.output(
                name = (event\"name").as[String],
                t = t,
                mimeType = (event\"mimeType").as[String],
                data = 
                  t match { 
                    case ArtifactType.PARAMETER => 
                      // Parameters are just raw JSON data that gets saved as-is
                      (event\"data").as[JsValue].toString.getBytes
                    case _ => 
                      (event\"data").as[String].getBytes
                  }
              )
              python.send("artifactId","artifactId" -> JsNumber(artifact.id))
            }
          case "delete_artifact" => 
            {
              context.delete( (event\"name").as[String] )
            }
          case "rename_artifact" => 
            {
              withArtifact { artifact => 
                if(context.artifactExists( (event\"newName").as[String] )){
                  val newName = (event\"newName").as[String]
                  context.error(s"Artifact '$newName' already exists")
                  python.kill()
                } else {
                  context.output( (event\"newName").as[String], artifact )
                  context.delete( (event\"name").as[String] )
                }
              }
            }
          case "get_data_frame" => 
            {
              withArtifact { artifact => 

                val connection = ArrowQuery(
                  QueryWithCaveats.build(
                    CatalogDB.withDB { implicit s => artifact.dataframe }(),
                    includeCaveats = false,
                    includeRowids = false,
                  )
                )

                python.send("data_frame",
                  "port" -> JsNumber(connection.port),
                  "secret" -> JsString(connection.secret)
                )
              }
            }
          case "create_file" =>
            {
              val file:Artifact = context.outputFilePlaceholder(
                (event \ "name").as[String],
                (event \ "mime").asOpt[String].getOrElse { "application/octet-stream" },
                JsObject(
                  (event \ "properties").asOpt[Map[String,JsValue]]
                                        .getOrElse { Map.empty }
                )
              )
              python.send("file_artifact",
                "artifactId" -> JsString(file.id.toString),
                "path" -> JsString(file.absoluteFile.toString),
                "url" -> JsString(file.url.toString)
              )

            }
          case x =>
            // stdinWriter.close()
            context.error(s"Received unknown event '$x': $event")
            python.kill()
        }
      } catch {
        case e: Exception => 
          {
            e match {
              case m:UnsupportedOperationException => 
                context.error(m.getMessage())
              case _ => 
                e.printStackTrace()
                context.error(s"INTERNAL ERROR: $e")
            }
            python.kill()
          }
      }
    } { logger.error(_) }

    if(ret != 0){
      context.error(s"Unexpected exit code $ret")
    }

    // io.cleanup()
    logger.debug("Done")
  }

  def predictProvenance(arguments: Arguments, properties: JsObject) =
    if(ExperimentalOptions.enabled("PARALLEL-PYTHON")){
      (
        (properties \ PROP_INPUT_PROVENANCE).asOpt[Seq[String]],
        (properties \ PROP_OUTPUT_PROVENANCE).asOpt[Seq[String]]
      ) match {
        case (Some(input), Some(output)) => 
          ProvenancePrediction.definitelyReads(input:_*)
                              .definitelyWrites(output:_*)
                              .andNothingElse
        case _ => ProvenancePrediction.default
      }
    } else { ProvenancePrediction.default }
}

