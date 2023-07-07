package info.vizierdb.dependencyAnalysis

import scala.io.Source
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.python.PythonProcess
import java.io.File
import info.vizierdb.Vizier
import info.vizierdb.commands.python.SystemPython
import info.vizierdb.commands.python.Pyenv
import info.vizierdb.catalog.PythonVirtualEnvironment
import info.vizierdb.commands.python.PythonDependency

class DependencySpec 
    extends Specification 
    with BeforeAll
{
    def beforeAll = SharedTestResources.init

    sequential
    "Simple Assign" >>
    {
        // PythonDependency("x=6") must beEqualTo("{'x':'inside'}").ignoreCase.ignoreSpace.trimmed
        PythonDependency("x=6") must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
    }

    "Simple If" >>
    {
        // val fileSource = Source.fromFile("test_data/dependency_test/if.py")
        // val script = fileSource.getLines.toIndexedSeq.mkString("\n")
        // fileSource.close
        // println(script)

        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import analyze
                   |source = open("test_data/dependency_test/if.py", "r")
                   |print(analyze(source.read()))
                   """.stripMargin).trim()
            test must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        ok
        // PythonDependency(script) must beEqualTo("{'y':'inside'}").ignoreCase.ignoreSpace.trimmed
    }

    "Simple Function" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
				  |sys.path.append("vizier/shared/resources")
				  |from dependency import analyze
				  |source = open("test_data/dependency_test/func.py", "r")
				  |print(analyze(source.read()))
				  |""".stripMargin)
            test must beEqualTo("[]")
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        ok
    }

    "Transitive Dependent Function" >> 
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
                   |sys.path.append("vizier/shared/resources")
                   |source = open("test_data/dependency_test/transitive_func.py", "r")
                   |from dependency import analyze
                   |print(analyze(source.read()))
                   |""".stripMargin)
                test must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        ok
    }
    "AugAssign" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import analyze
                   |print(analyze("x += 5"))
                   |""".stripMargin)
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        test must beEqualTo("[]")
    }
    "AnnAssign" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import analyze
                   |print(analyze("x: int"))
                   |""".stripMargin)
                test must beEqualTo("['int']")
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        ok
    }

}