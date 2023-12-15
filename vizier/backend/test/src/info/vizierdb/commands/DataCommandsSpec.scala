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

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import play.api.libs.json._
import java.io.File

import scalikejdbc._

import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.catalog.{ Project, Module }
import info.vizierdb.viztrails.Scheduler
import info.vizierdb.MutableProject

class DataCommandsSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  sequential

  "load, unload, and query data" >> {
    val project = MutableProject("Data Project")

    project.append("data", "load")(
      "file" -> FileArgument.fromUrl("test_data/r.csv"),
      "name" -> "test_r",
      "loadFormat" -> "csv",
      "loadInferTypes" -> true,
      "loadDetectHeaders" -> true
    )
    project.waitUntilReadyAndThrowOnError
    
    {
      val workflow = project.head
      val lastModule =
        DB readOnly { implicit s => 
          workflow.modulesInOrder
                  .last
        }
      lastModule.arguments.value("schema").as[Seq[JsValue]] must not beEmpty
    }

    project.append("data", "clone")(
      "dataset" -> "test_r",
      "name" -> "clone_r",
    )
    project.waitUntilReadyAndThrowOnError

    project.append("data", "unload")(
      "dataset" -> "clone_r",
      "unloadFormat" -> "csv"
    )
    project.waitUntilReadyAndThrowOnError

    project.append("data", "empty")(
      "name" -> "empty_ds"
    )
    project.waitUntilReadyAndThrowOnError

    project.append("sql", "query")(
      "source" -> "SELECT * FROM test_r",
      "output_dataset" -> "query_result"
    )
    project.waitUntilReadyAndThrowOnError

    project.append("sql", "query")(
      "source" -> "SELECT * FROM empty_ds, test_r",
      "output_dataset" -> "query_result"
    )
    project.waitUntilReadyAndThrowOnError

    ok
  }

  "unload files" >> {
    val project = MutableProject("File Project")

    project.script("""
      |with vizierdb.create_file("test.csv") as f:
      |  f.write("1,2\n")
      |  f.write("3,4\n")
      """.stripMargin)

    project.waitUntilReadyAndThrowOnError
    project.artifacts.keys must contain("test.csv")

    val f = File.createTempFile("test", ".csv")
    if(f.exists){
      f.delete
    }
    f.exists must beFalse
    f.deleteOnExit
    project.append("data", "unloadFile")(
      "file" -> "test.csv",
      "path" -> f.toString
    )
    project.waitUntilReadyAndThrowOnError
    f.exists must beTrue
  }

  "manage parameters" >> {
    val project = MutableProject("Parameter Project")
    
    project.setParameters(
      "foo" -> "floop",
      "bar" -> 23.7,
      "baz" -> 999
    )

    project.script("""
      |print(vizierdb["foo"])
      |print(vizierdb["bar"])
      |print(vizierdb["baz"])
    """.stripMargin)

    project.waitUntilReadyAndThrowOnError

    project.lastOutput.map { _.dataString } must contain(exactly("floop", "23.7", "999"))
  }

  "load json data" >> {
    val project = MutableProject("Load JSON")

    project.load(
      file = "test_data/simple.json",
      name = "test",
      format = "json",
      inferTypes = false,
    )

    project.dataframe("test").count() must beGreaterThan(1l)
  }

  "load csv with non-standard delimiters" >> {
    val project = MutableProject("Load Nonstandard CSV")

    project.load(
      file = "test_data/semicolons.csv",
      name = "test", 
      format = "csv",
      arguments = Seq("delimiter" -> ";")
    )

    val df = project.dataframe("test")

    // df.show()?
    df.collect().map { _.getString(0) }.toSet must contain(exactly("Fall 2015"))

    ok
  }
}

