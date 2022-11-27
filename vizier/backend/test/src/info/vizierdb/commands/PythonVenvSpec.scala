package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.python.PythonProcess
import java.io.File
import info.vizierdb.Vizier
import info.vizierdb.commands.python.SystemPython
import info.vizierdb.commands.python.Pyenv
import info.vizierdb.catalog.PythonVirtualEnvironment

class PythonVenvSpec 
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init
  
  val envName = "test_venv"
  val testPackage = "urllib3"

  // Avoid relying on the network during test cases unless the user
  // asks nicely.
  skipAllUnless(
    new File("./.vizier_test_venv").isFile()
  )

  sequential

  "System Python - List Packages" >> 
  {
    SystemPython.packages.map { _._1 } must contain("bokeh")
  }

  "Work with pyenv" >> 
  {
    // we should have at least one 3.x version installed.
    Pyenv.installed.map { _.split("\\.")(0) } must contain("3")
    Pyenv.versions must contain(eachOf("2.4.1", "3.9.0"))
    // PyEnv.uninstall("3.10.5")
    // PyEnv.installed must not contain("3.10.5")
    // PyEnv.install("3.10.5")
    // PyEnv.installed must contain("3.10.5")
  }

  val venv = PythonVirtualEnvironment(0l, "test_venv", Pyenv.installed.last, 0l, Seq.empty)

  "Create Venv" >>
  {
    println(s"Creating Venv at ${venv.version}")
    venv.init(true)

    (
      new File(venv.bin, "activate")
    ).isFile() must beTrue
  }

  "Install a package into the venv" >>
  {
    venv.Environment.install(testPackage)

    PythonProcess.run(environment = venv.Environment,
      script = """import urllib3
                 |print("hi!")""".stripMargin
    ).trim() must beEqualTo("hi!")
  }
}