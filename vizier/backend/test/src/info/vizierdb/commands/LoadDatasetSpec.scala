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

import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.apache.spark.sql.types.IntegerType
import info.vizierdb.Vizier

class LoadDatasetSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  sequential

  "Spark should infer schemas" >> {
    val df = Vizier.sparkSession.read
                   .option("header", "true")
                   .option("inferSchema", "true")
                   .csv("test_data/r.csv")
    df.schema.fields(0).name must beEqualTo("A")
    df.schema.fields(0).dataType must beEqualTo(IntegerType)
  }

  "Load Dataset should autodetect schemas" >> {
    val project = MutableProject("Load Dataset")
    project.load("test_data/r.csv", name = "r")
    val df = project.dataframe("r")
    df.schema.fields(0).dataType must beEqualTo(IntegerType)
    ok
  }
}