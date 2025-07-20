/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.viztrails

import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.util.TimerUtils.time
import info.vizierdb.util.ExperimentalOptions
import info.vizierdb.commands.python.Python


class ParallelPythonSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

    // Enabling PARALLEL-PYTHON globally during tests shouldn't be a problem, since it only triggers
  // if the input/output provenance properties are set.  
  ExperimentalOptions.enable("PARALLEL-PYTHON")

  "run sequentially without provenance" >> {

    val project = MutableProject("ParallelPython-Sequential")

    val (_, t) = time {
      project.script(
        """import time
          |time.sleep(2)
          """.stripMargin,
        waitForResult = false)

      project.script(
        """import time
          |time.sleep(2)
          """.stripMargin,
        waitForResult = false)

      project.waitUntilReadyAndThrowOnError
    }

    println(s"Sequential: ${t / 1000000.0}ms")
    // We should spend at least 3 seconds processing the above script 
    // (add a 1.5s buffer for launching python/scheduling/etc...)
    t must beGreaterThan(3500000000l)

  }

  "run in parallel with provenance" >> {

    val project = MutableProject("ParallelPython-Parallel w/o Data")

    val (_, t) = time {
      project.script(
        """import time
          |time.sleep(2)
          """.stripMargin,
        waitForResult = false,
        properties = Map(
          Python.PROP_INPUT_PROVENANCE -> JsArray(Seq()),
          Python.PROP_OUTPUT_PROVENANCE -> JsArray(Seq())
        )
      )

      project.script(
        """import time
          |time.sleep(2)
          """.stripMargin,
        waitForResult = false)

      project.waitUntilReadyAndThrowOnError
    }

    println(s"Parallel: ${t / 1000000.0}ms")
    // The scripts should execute in parallel.
    t must beLessThan(3900000000l)

  }

  "run in parallel with provenance and dependencies" >> {

    val project = MutableProject("ParallelPython-Parallel w/ Data")

    val (_, t) = time {
      project.script(
        """import time
          |vizierdb["x"] = "johnny"
          |time.sleep(4)
          """.stripMargin,
        waitForResult = false,
        properties = Map(
          Python.PROP_INPUT_PROVENANCE -> JsArray(Seq()),
          Python.PROP_OUTPUT_PROVENANCE -> JsArray(Seq(JsString("x")))
        )
      )

      project.script(
        """import time
          |vizierdb["y"] = 5
          |time.sleep(4)
          """.stripMargin,
        waitForResult = false,
        properties = Map(
          Python.PROP_INPUT_PROVENANCE -> JsArray(Seq()),
          Python.PROP_OUTPUT_PROVENANCE -> JsArray(Seq(JsString("y")))
        )
      )

      project.script(
        """print("{}-{}".format(vizierdb["x"], vizierdb["y"]))
          """.stripMargin,
        waitForResult = false,
        properties = Map(
          Python.PROP_INPUT_PROVENANCE -> JsArray(Seq(JsString("x"), JsString("y"))),
          Python.PROP_OUTPUT_PROVENANCE -> JsArray(Seq())
        )
      )

      project.waitUntilReadyAndThrowOnError
    }

    println(s"Parallel w/ data: ${t / 1000000.0}ms (output = ${project.lastOutputString.trim()})")
    // The scripts should execute in parallel.
    t must beLessThan(7900000000l)

    project.lastOutputString must contain("johnny-5")

  }


}
