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
package info.vizierdb.commands

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.apache.spark.sql.types._

class LensCommandsSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "Cast to integers" >> {
    val project = MutableProject("LensCommands - Cast to Integers")
    project.load("test_data/r.csv", "R", inferTypes = false)
    project.dataframe("R").schema(0).dataType should beEqualTo(StringType)
    
    project.append("mimir", "type_inference")(
      "dataset" -> "R",
      "schema" -> Seq(
        Map(
          "schema_column" -> 0,
          "schema_datatype" -> "int"
        )
      )
    )
    project.waitUntilReadyAndThrowOnError

    project.dataframe("R").schema(0).dataType must beEqualTo(IntegerType)

  }
}
