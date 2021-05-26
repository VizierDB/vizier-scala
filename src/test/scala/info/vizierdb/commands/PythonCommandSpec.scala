/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.commands

import scalikejdbc.DB
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.viztrails.MutableProject
import info.vizierdb.commands.python.PythonProcess
import org.apache.spark.sql.types._
import org.mimirdb.api.MimirAPI

class PythonCommandSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  lazy val project = MutableProject("Data Project")
  sequential


  "have an up-to-date requirements.txt" >> 
  {
    val test =
      PythonProcess.REQUIRED_PACKAGES
                   .map { _._2 }
                   .toSet
                   .toSeq
    val requirements =
      scala.io.Source.fromInputStream(
        getClass()
          .getClassLoader()
          .getResource("requirements.txt")
          .openStream()
      ).getLines()
       .toSet
       .toSeq

    test must containTheSameElementsAs(requirements)
  }

  "run simple python scripts" >> 
  {
    project.script("""
print("Hello Wookie")
""")
    project.lastOutputString must beEqualTo("Hello Wookie")
  }

  "share python functions" >>
  {
    project.script("""
def foo(bar):
  print("YY: "+bar)
foo("x")
vizierdb.export_module(foo)
""")
    project.lastOutputString must beEqualTo("YY: x")

    project.script("""
vizierdb["foo"]("z")
""")
    project.lastOutputString must beEqualTo("YY: z")

    project.script("""
foo("w")
""")
    project.lastOutputString must beEqualTo("YY: w")
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

    project.lastOutputString must beEqualTo("<A(short), C(short)> (7 rows)")

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

  "Arrow DataFrames" >>
  {
    skipped("Pyarrow 4.0.0 doesn't seem to be compatible with us.  Suppressing until I have internet again. [OK, 2021-05-26]")
    project.script("""
df = vizierdb.get_data_frame("q")
print(df['A'].sum())
""")
    project.lastOutputString must beEqualTo("12")
  }

  "Export Pandas" >>
  {
    project.script("""
      |import pandas as pd
      |dfa = pd.DataFrame.from_records([
      |    {"a": x, "c": str(x)}
      |    for x in range(0, 10)
      |])
      |dfb = pd.DataFrame.from_records([
      |    {"b": x}
      |    for x in range(0, 10000)
      |])
      |vizierdb.save_data_frame("little_data", dfa)
      |vizierdb.save_data_frame("big_data", dfb)
    """.stripMargin)
    
    {
      val art = project.artifact("little_data")
      art.t must beEqualTo(ArtifactType.DATASET)
      val ds = art.getDataset()
      ds.schema must containTheSameElementsAs(Seq(
        StructField("a", LongType),
        StructField("c", StringType)
      ))
      ds.data
        .map { _(0) } must containTheSameElementsAs(
          (0 until 10).toSeq
        )
    }
    
    {
      val art = project.artifact("big_data")
      val df = MimirAPI.catalog.get(art.nameInBackend)
      df.columns.toSeq must containTheSameElementsAs(Seq("b"))
      df.count() must beEqualTo(10000)
      df.distinct().count() must beEqualTo(10000)
    }
  }

  "Export Pickles" >> 
  {
    project.script("""
      |a = 1
      |b = "hello world"
      |c = [
      |      { "x" : i, "y" : "foo" } 
      |      for i in range(0, 100)
      |    ]
      |vizierdb.export_pickle("pickle_a", a)
      |vizierdb.export_pickle("pickle_b", b)
      |vizierdb.export_pickle("pickle_c", c)
    """.stripMargin)

    project.script("print(vizierdb.get_pickle(\"pickle_a\"))")
    project.lastOutputString must beEqualTo("1")

    project.script("print(vizierdb.get_pickle(\"pickle_b\"))")
    project.lastOutputString must beEqualTo("hello world")

    project.script("print(vizierdb.get_pickle(\"pickle_c\")[23][\"x\"])")
    project.lastOutputString must beEqualTo("23")
  }

}

