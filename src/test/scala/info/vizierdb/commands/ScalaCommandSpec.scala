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

import scala.collection.mutable
import info.vizierdb.commands.jvmScript._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject

class ScalaCommandSpec
  extends Specification
  with BeforeAll
{

  def beforeAll = SharedTestResources.init()

  sequential 

  "Barebones Scala Execution" >> {
    val output = mutable.Buffer[String]()
    val ctx = new ExecutionContext(
                    1, 
                    Map.empty, 
                    null, 
                    null, 
                    null, 
                    { (mime, data) => output += new String(data) },
                    { (err) => println(err) }
                  )
    ScalaScript.eval("""
      println("yo yo yo")
    """, ctx)
    output.mkString must beEqualTo("yo yo yo\n")
  }

  "Execution in a workflow" >> {
    val project = MutableProject("Scala Command Test")

    project.load("test_data/r.csv", "R")
    project.script(
      """import info.vizierdb.types._
        |
        |val r = vizierdb.dataframe("R")
        |println(r.count())
        |vizierdb.output("S", ArtifactType.BLOB, "quirk".getBytes)
        |""".stripMargin, language = "scala") 
    project(1).get.map { _.dataString }.mkString must beEqualTo("7\n")
    project.artifact("S").data must beEqualTo("quirk".getBytes)
  }
}

