package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.python.PythonProcess
import java.io.File
import info.vizierdb.Vizier

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

  "Create Venv" >>
  {
    PythonProcess.venv.create(envName, overwrite = true)

    (
      new File(PythonProcess.venv.bin(envName), "activate")
    ).isFile() must beTrue
  }

  "Install a package into the venv" >>
  {
    PythonProcess.venv.install(envName, testPackage)

    PythonProcess.run(envName = envName,
      script = """import urllib3
                 |print("hi!")""".stripMargin
    ).trim() must beEqualTo("hi!")
  }
}