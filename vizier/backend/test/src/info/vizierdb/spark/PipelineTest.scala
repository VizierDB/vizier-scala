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
package info.vizierdb.spark

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import scala.collection.mutable
import info.vizierdb.commands.jvmScript._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.apache.spark.sql.types.IntegerType

class PipelineTest
  extends Specification
  with BeforeAll
{

  def beforeAll = SharedTestResources.init()

  "Create and save a simple pipeline" >>
  {
    val project = MutableProject("pipeline test")

    project.load(
      "test_data/r.csv",
      "r",
      schema = Seq(
        "A" -> IntegerType,
        "B" -> IntegerType,
        "C" -> IntegerType,
      )
    )

    project.script(
      """val a = vizierdb.createPipeline("r")(
        |  new org.apache.spark.ml.feature.OneHotEncoder()
        |    .setInputCols(Array("A"))
        |    .setOutputCols(Array("D"))
        |)
        |vizierdb.displayDataset("r")
        |""".stripMargin, 
      language = "scala"
    )

    project.dataframe("r").show()
    ok
  }
}