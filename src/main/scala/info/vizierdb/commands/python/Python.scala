package info.vizierdb.commands.python

import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import org.mimirdb.api.request.CreateViewRequest
import com.typesafe.scalalogging.LazyLogging

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
    val interface = PythonInterface(script, context)
    val python = PythonProcess()

    python.send("script", "script" -> JsString(script), "artifacts" -> 
      JsObject(context.scope.mapValues { artifact => 
        Json.obj(
          "nameInBackend" -> artifact.nameInBackend,
          "file" -> artifact.file.toString,
          "type" -> artifact.t.toString
        )
      })
    )

    val ret = python.monitor { event => 
      logger.debug(s"STDIN: $event")
      (event\"event").as[String] match {
        case "message" => 
          (event\"stream").as[String] match {  
            case "stdout" => context.message( (event\"content").as[String] )
            case "stderr" => context.error( (event\"content").as[String] )
            case x => context.error(s"Received message on unknown stream '$x'")
          }
        case x =>
          // stdinWriter.close()
          context.error(s"Received unknown event '$x': $event")
      }
    } { logger.error(_) }

    if(ret != 0){
      context.error("Unexpected exit code $ret")
    }

    // io.cleanup()
    logger.debug("Done")
  }
}