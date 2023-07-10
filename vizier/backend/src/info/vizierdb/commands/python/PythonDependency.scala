package info.vizierdb.commands.python

import play.api.libs.json._

object PythonDependency {
    def apply(script: String): String= 
    {
        val python = PythonProcess()

        python.send("dependency", 
            "script" -> JsString(script)
        )

        python.watchForErrors(println(_))

        val response = python.read()
        response match {
            case Some(s) => (s \ "content").as[String]
            case None => "Error None"
        }
    }
}
