package info.vizierdb.commands

import scalikejdbc.DB
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.viztrails.MutableProject

class PythonCommandSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  lazy val project = MutableProject("Data Project")
  sequential

  def script(script: String) = 
  {
    project.append("script", "python")("source" -> script)
    project.waitUntilReady
  }
  def lastOutput =
  {
    val workflow = project.head
    DB.readOnly { implicit s => 
      workflow.cells.reverse.head.messages
    }
  }
  def lastOutputString =
    lastOutput.map { _.dataString }.mkString

  "run simple python scripts" >> 
  {
    script("""
print("Hello Wookie")
""")
    
    lastOutputString must beEqualTo("Hello Wookie\n")
  }

  "interact with datasets" >> 
  {
    project.append("data", "load")(
      "file" -> "test_data/r.csv",
      "name" -> "test_r",
      "loadFormat" -> "csv",
      "loadInferTypes" -> true,
      "loadDetectHeaders" -> true
    )
    project.waitUntilReady

    script("""
ds = vizierdb["test_r"]
print("success: {} / {}".format(
  ds.get_column("A"),
  ds["shazbot"]
))
print("A at: {}".format(ds.column_index("A")))
print("1 at: {}".format(ds.column_index(1)))

ds.delete_column("B")

print(ds)
for row in ds.rows():
  print(row)

ds.save("Q")
""")

    print(lastOutputString)

    lastOutputString.split("\n").toSeq must contain(eachOf(
      "success: A(short) / None",
      "A at: 0",
      "1 at: 1",
      "<1, 1>"
    ))

    script("ds = vizierdb[\"Q\"];print(ds)")

    lastOutputString must beEqualTo("<A(short), C(short)> (7 rows)\n")

    script("""
ds = vizierdb["test_r"]
print(ds.to_bokeh())
""")

    lastOutputString must beEqualTo("floop")
  }

}