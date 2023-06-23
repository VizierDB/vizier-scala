import info.vizierdb.commands.python.PythonProcess


import info.vizierdb.test.SharedResources
import org.specs2.control.eff.syntax.console


class DependencyAnalysis extends Specification {
    def beforeAll = SharedTestResources.init

    sequential
    "open and visit a python file" >>
    {
        var test = ""
        try {
            test = PythonProcess.run(
                """import os
                |import ast
                |os.chdir("target/scala-2.12/classes")
                |from dependencyAnalysis import Visit_AST
                |
                |tree = ast.parse("x=5")
                |vis = Visit_AST()
                |vis.visit(tree)
                |print("store: {} \n".format(vis.main_dict_store))
                |""".stripMargin
            )
            ok
        }catch {
            case esc: Throwable => println("Running python process failed with error: \n" + exc)
            failure
        }
        print(test)
        ok
    }
}