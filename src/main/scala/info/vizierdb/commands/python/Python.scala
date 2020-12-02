package info.vizierdb.commands.python

import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.types._
import info.vizierdb.filestore.Filestore
import org.mimirdb.api.request.{ 
  CreateViewRequest, 
  LoadInlineRequest, 
  QueryTableRequest,
  QueryDataFrameRequest
}
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.types.StructField
import org.mimirdb.spark.SparkPrimitive
import org.mimirdb.spark.Schema.fieldFormat
import info.vizierdb.catalog.Artifact

object Python extends Command
  with LazyLogging
{
  def name: String = "Python Script"
  def parameters: Seq[Parameter] = Seq(
    CodeParameter(id = "source", language = "python", name = "Python Code"),
    // StringParameter(id = "output_dataset", name = "Output Dataset", required = false)
  )
  def format(arguments: Arguments): String = 
    arguments.pretty("source")
  def process(arguments: Arguments, context: ExecutionContext): Unit = 
  {
    logger.debug("Initializing...")
    val script = arguments.get[String]("source")
    val python = PythonProcess()

    python.send("script", 
      "script" -> JsString(script), 
      "artifacts" -> 
        JsObject(context.scope.mapValues { artifact => 
          Json.obj(
            "type" -> artifact.t.toString(),
            "mimeType" -> artifact.mimeType,
            "artifactId" -> artifact.id
          )
        }),
      "projectId" -> JsNumber(context.projectId)
    )

    val ret = python.monitor { event => 
      logger.debug(s"STDIN: $event")
      try { 
        (event\"event").as[String] match {
          case "message" => 
            (event\"stream").as[String] match {  
              case "stdout" => 
                context.message( 
                  content = (event\"content").as[String],
                  mimeType = (event\"mimeType").getOrElse { JsString(MIME.TEXT) }
                                              .as[String]
                )
              case "stderr" => context.error( (event\"content").as[String] )
              case x => context.error(s"Received message on unknown stream '$x'")
            }
          case "get_dataset" => 
            context.artifact( (event\"name").as[String] ) match {
              case None => 
                val name = (event\"name").as[String]
                context.error("No such dataset '$name'")
                python.kill()
              case Some(artifact) => 
                python.send("dataset",
                  "data" -> Json.toJson(
                    artifact.getDataset(includeUncertainty = true)
                  ),
                  "artifactId" -> JsNumber(artifact.id)
                )
            }
          case "save_dataset" =>
            {
              val (nameInBackend, id) = 
                context.outputDataset( (event\"name").as[String] )

              val ds = (event\"dataset")
              LoadInlineRequest(
                schema = (ds\"schema").as[Seq[StructField]],
                data = (ds\"data").as[Seq[Seq[JsValue]]],
                dependencies = None,
                resultName = Some(nameInBackend),
                properties = Some( (ds\"properties").as[Map[String,JsValue]] ),
                humanReadableName = Some( (event\"name").as[String] )
              ).handle

              python.send("datasetId",
                "artifactId" -> JsNumber(id)
              )
            }
          case "save_artifact" =>
            {
              val id = context.output(
                name = (event\"name").as[String],
                t = ArtifactType.withName( (event\"artifactType").as[String] ),
                mimeType = (event\"mimeType").as[String],
                data = (event\"data").as[String].getBytes
              )
            }
          case "delete_artifact" => 
            {
              context.delete( (event\"name").as[String] )
            }
          case "rename_artifact" => 
            {
              context.artifact( (event\"name").as[String] ) match {
                case None => 
                  val name = (event\"name").as[String]
                  context.error(s"No such dataset '$name'")
                  python.kill()
                case Some(artifact) => 
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
              context.artifact( (event\"name").as[String] ) match {
                case None => 
                  val name = (event\"name").as[String]
                  context.error(s"No such dataset '$name'")
                  python.kill()
                case Some(artifact) => 
                  val response = QueryDataFrameRequest(
                    input = None,
                    query = s"SELECT * FROM ${artifact.nameInBackend}",
                    includeUncertainty = Some(true),
                    includeReasons = Some(false)
                  ).handle
                  python.send("data_frame",
                    "port" -> JsNumber(response.port),
                    "secret" -> JsString(response.secret)
                  )
              }
            }
          case x =>
            // stdinWriter.close()
            context.error(s"Received unknown event '$x': $event")
        }
      } catch {
        case e: Exception => 
          {
            e.printStackTrace()
            context.error(s"INTERNAL ERROR: $e")
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
}