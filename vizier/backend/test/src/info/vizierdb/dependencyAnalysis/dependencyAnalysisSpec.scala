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
    "Simple Assign" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import os
                |os.chdir("vizier/shared/resources")
                |from dependencyAnalysis import Visit_AST
                |import ast
                |tree = ast.parse("x=5")
                |vis = Visit_AST()
                |vis.visit(tree)
                |print(vis.scope_stack[0])
                |""".stripMargin).trim() 
            test must beEqualTo("{'x':'inside'}").ignoreCase.ignoreSpace.trimmed
        }catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        ok
    }

    "Simple If" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import os
                |import ast
                |with open("test_data/if.py", "r") as source:
                |   tree = ast.parse(source.read())
                |os.chdir("vizier/shared/resources")
                |from dependencyAnalysis import Visit_AST
                |vis = Visit_AST()
                |vis.visit(tree)
                |print(vis.scope_stack[0])
                """.stripMargin)
                test must beEqualTo("{'y':'inside'}").ignoreCase.ignoreSpace.trimmed
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        ok
    }
}