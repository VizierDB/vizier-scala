package info.vizierdb.dependencyAnalysis

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.python.PythonProcess
import java.io.File
import info.vizierdb.Vizier
import info.vizierdb.commands.python.SystemPython
import info.vizierdb.commands.python.Pyenv
import info.vizierdb.catalog.PythonVirtualEnvironment

class DependencyAnalysisSpec 
    extends Specification 
    with BeforeAll
{
    def beforeAll = SharedTestResources.init

    sequential
    "open and visit a python file" >>
    {
        var test = ""
        // try {
            test = PythonProcess.run(
                """import os
                |print(os.getcwd())
                |os.chdir("vizier/shared/resources")
                |import dependencyAnalysis
                |""".stripMargin)
            // ok
        // }catch {
            // case exc: Throwable => println("Running python process failed with error: \n" + exc)
            // failure
        // }
        print(test)
        ok
    }
}