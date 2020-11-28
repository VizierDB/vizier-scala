package info.vizierdb.commands.python

import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.commands._
import info.vizierdb.filestore.Filestore
import org.mimirdb.api.request.CreateViewRequest
import com.typesafe.scalalogging.LazyLogging
import org.python.util.PythonInterpreter

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
    val script = arguments.get[String]("source")

    logger.debug("Initializing Interpreter")
    val interpreter = new PythonInterpreter()
    logger.debug("Initializing Scope")
    val client = PythonClient(context, interpreter)
    interpreter.set("vizierdb", client)
    interpreter.setOut(client.Stdout)
    interpreter.setErr(client.Stderr)

    logger.debug("Running Script")
    interpreter.exec(script)
    logger.debug("Done")

  }
}