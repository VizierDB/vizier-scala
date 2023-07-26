package info.vizierdb.commands.python

import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._
import info.vizierdb.python.DependencyResponse

object PythonDependency 
    extends LazyLogging
{

    def apply(script: String): DependencyResponse = 
    {
        val python = PythonProcess()

        python.send("dependency", 
            "script" -> JsString(script)
        )
        println("here\n\n")

        python.watchForErrors(logger.error(_))

        val response = python.read()
        response match {
            case Some(s) => {
                try {
                    s.as[DependencyResponse]
                } catch {
                    case err: Throwable => logger.error("Error: some exception"); 
                    DependencyResponse(Seq("err") , Map("err" -> 0))
                }
            }
            case None => null
        }
    }
}
