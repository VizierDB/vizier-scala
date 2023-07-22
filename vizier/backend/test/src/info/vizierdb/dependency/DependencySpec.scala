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
import play.api.libs.json._
import info.vizierdb.python.JupyterNotebook
import info.vizierdb.python.JupyterCell
import info.vizierdb.MutableProject


class DependencySpec 
    extends Specification 
    with BeforeAll
{
    def beforeAll = SharedTestResources.init

    sequential
    "Vizier Integration" >>
    {
        PythonDependency("x=5") must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
    }

    "Simple Assign" >>
    {
        PythonDependency("x=6") must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
    }

    "Simple If" >>
    {
        val fileSource = Source.fromFile("test_data/dependency_test/if.py")
        val script = fileSource.getLines.toIndexedSeq.mkString("\n") 
        fileSource.close

        PythonDependency(script) must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
    }

    "Simple Function" >>
    {
        val fileSource = Source.fromFile("test_data/dependency_test/func.py")
        val script = fileSource.getLines.toIndexedSeq.mkString("\n") 
        fileSource.close

        PythonDependency(script) must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
    }

    "Transitive Dependent Function" >> 
    {
        val fileSource = Source.fromFile("test_data/dependency_test/transitive_func.py")
        val script = fileSource.getLines.toIndexedSeq.mkString("\n") 
        fileSource.close

        PythonDependency(script) must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
    }

    "AugAssign" >>
    {
        val fileSource = Source.fromFile("test_data/dependency_test/aug_assign.py")
        val script = fileSource.getLines.toIndexedSeq.mkString("\n") 
        fileSource.close

        PythonDependency(script) must beEqualTo("[]").ignoreCase.ignoreSpace.trimmed
    }

    "AnnAssign" >>
    {
        PythonDependency("x: int") must beEqualTo("['int']")
    }

    "Mutable Project Test" >>
    {
        val fileSource = Source.fromFile("test_data/dependency_test/MutableProjectTest.ipynb")
        val script = fileSource.getLines.toIndexedSeq.mkString("\n") 
        fileSource.close

        val json = Json.parse(script)
        val nb = json.as[JupyterNotebook]

        val project = MutableProject("Jupyter Notebook Test")

        for(cell <- nb.cells)
        {
            cell.cell_type match {
                case "markdown" => 
                    project.markdown(cell.toString())
                case "code" => 
                    project.script(cell.source.toIndexedSeq.mkString("\n"))

            }
        }

        project.lastOutputString must beEqualTo("Hello, World!")
    }
}