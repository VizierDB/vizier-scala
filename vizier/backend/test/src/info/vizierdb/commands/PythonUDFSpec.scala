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

import info.vizierdb.test.SharedTestResources
import info.vizierdb.commands.python.PythonUDFBuilder
import info.vizierdb.Vizier
import org.apache.spark.sql.Column

class PythonUDFSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init


  "Build and run a UDF" >> 
  {
    val pyToUDF = PythonUDFBuilder()
    val pickle = pyToUDF.pickle(
      """def foo(bar: int) -> int:
        |  return bar * bar
        """.stripMargin)
    pickle must haveSize(greaterThan(0))
    pyToUDF.getName(pickle) must beEqualTo("foo")
    pyToUDF.getType(pickle) must beEqualTo(org.apache.spark.sql.types.IntegerType)

    pyToUDF.runPickle(pickle, "2") must beEqualTo("4")

    val udf = pyToUDF.apply(pickle)

    val df = Vizier.sparkSession.range(10)

    val applied = df.select(
                    new Column(udf(Seq( 
                      df(df.columns(0)).expr
                    )))
                  ).collect()

    ok


  }
}