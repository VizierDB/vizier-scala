package info.vizierdb.commands.python

// import info.vizierdb.commands.python.PythonProcess

object PythonDependency {
    def apply(s: String): String = {
        try {
            PythonProcess.run(
            s"""import sys
            |sys.path.append("vizier/shared/resources")
            |from dependency import analyze
            |print(analyze("$s"))
            """.stripMargin).trim()
        } catch {
            case exc: Throwable => "Running python process failed with error: \n" + exc
        }
    }
}
