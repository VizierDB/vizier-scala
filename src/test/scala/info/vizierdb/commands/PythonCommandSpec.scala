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


  "run simple python scripts" >> 
  {
    project.script("""
print("Hello Wookie")
""")
    project.lastOutputString must beEqualTo("Hello Wookie\n")
  }

  "share python functions" >>
  {
    project.script("""
def foo(bar):
  print("YY: "+bar)
foo("x")
vizierdb.export_module(foo)
""")
    project.lastOutputString must beEqualTo("YY: x\n")

    project.script("""
vizierdb["foo"]("z")
""")
    project.lastOutputString must beEqualTo("YY: z\n")

    project.script("""
foo("w")
""")
    project.lastOutputString must beEqualTo("YY: w\n")
  }


  "interact with datasets" >> 
  {
    project.load("test_data/r.csv", "test_r")

    project.script("""
ds = vizierdb["test_r"]
print("success: {} / {}".format(
  ds.get_column("A"),
  ds["shazbot"]
))
print("A at: {}".format(ds.column_index("A")))
print("1 at: {}".format(ds.column_index(1)))

ds.delete_column("B")

print(ds)
for row in ds.rows:
  print(row)

ds.save("Q")
""")
    project.artifactRefs.map { _.userFacingName } must contain("q")

    project.lastOutputString.split("\n").toSeq must contain(eachOf(
      "success: A(short) / None",
      "A at: 0",
      "1 at: 1",
      "<1, 1>"
    ))

    project.script("ds = vizierdb[\"Q\"];print(ds)")

    project.lastOutputString must beEqualTo("<A(short), C(short)> (7 rows)\n")

    project.artifactRefs.map { _.userFacingName } must contain("test_r")
    project.script("""
vizierdb.drop_dataset("test_r")
""")
    project.artifactRefs.map { _.userFacingName } must not contain("test_r")
  }

  "basic 'show' outputs" >> 
  {
    project.script("""
ds = vizierdb["q"]
print(ds.to_bokeh())
""")
    project.lastOutputString must startWith("ColumnDataSource")

    project.script("""
show(vizierdb["q"])
""")
    project.lastOutput.map { _.mimeType } must contain(MIME.DATASET_VIEW)
  }


}