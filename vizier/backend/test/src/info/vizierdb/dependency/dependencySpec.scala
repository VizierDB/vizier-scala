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

class DependencySpec 
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
                """import sys
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import Visit_AST
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
        print("Test: ", test)
        ok
    }

    "Simple If" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
                   |import ast
                   |with open("test_data/dependency_test/if.py", "r") as source:
                   |   tree = ast.parse(source.read())
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import Visit_AST
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

    "Simple Function" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
				  |import ast
				  |with open("test_data/dependency_test/func.py", "r") as source:
				  |   tree = ast.parse(source.read())
				  |sys.path.append("vizier/shared/resources")
				  |from dependency import Visit_AST
				  |vis = Visit_AST()
				  |vis.visit(tree)
				  |print(vis.scope_stack[0])
				  |""".stripMargin)
            test must beEqualTo("{'function': ('inside', [])}")
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
                   |import ast
                   |with open("test_data/dependency_test/transitive_func.py", "r") as source:
                   |   tree = ast.parse(source.read())
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import Visit_AST
                   |vis = Visit_AST()
                   |vis.visit(tree)
                   |print(vis.scope_stack[0])
                   |""".stripMargin)
                test must beEqualTo("{'x': 'inside', 'func': ('inside', ['x'])}").ignoreCase.ignoreSpace.trimmed
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
                   |import ast
                   |tree = ast.parse("x += 5")
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import Visit_AST
                   |vis = Visit_AST()
                   |vis.visit(tree)
                   |print(vis.scope_stack[0])
                   |""".stripMargin)
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        test must beEqualTo("{'x': 'inside'}")
    }
    "AnnAssign" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import sys
                   |import ast
                   |tree = ast.parse("x: int")
                   |sys.path.append("vizier/shared/resources")
                   |from dependency import Visit_AST
                   |vis = Visit_AST()
                   |vis.visit(tree)
                   |print(vis.scope_stack[0])
                   |""".stripMargin)
                test must beEqualTo("{'x': 'inside', 'int': 'outside'}")
        } catch {
            case exc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        ok
    }

}