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
package info.vizierdb.catalog

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.catalog.gc._

class GCSpec extends Specification with BeforeAll
{
  def beforeAll = SharedTestResources.init


  "Deduplicate Files" >> {
    val project = MutableProject("Deduplicate Files")

    project.load(
      "test_data/r.csv",
      "r1",
      inferTypes = false,
      waitForResult = false,
      copyFile = true
    )
    project.load(
      "test_data/r.csv",
      "r2",
      inferTypes = false,
      waitForResult = false,
      copyFile = true
    )
    project.load(
      "test_data/simple.json",
      "r3",
      format = "json",
      inferTypes = false,
      waitForResult = false,
      copyFile = true
    )

    println("Waiting for load")
    project.waitUntilReadyAndThrowOnError
    println("Done loading")

    DedupFiles(project.projectId)

    project.dataframe("r1").count() must beEqualTo(7l)
    project.dataframe("r2").explain()
    project.dataframe("r2").count() must beEqualTo(7l)
  }

}
