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
import org.specs2.specification.core.Fragment

class SQLSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "Run queries" >> 
  {
    val workflow = MutableProject("Run queries")
    workflow.load("test_data/r.csv", "foo")
    workflow.sql("SELECT count(*) FROM foo" -> "foo")
    workflow.waitUntilReadyAndThrowOnError
    workflow.dataframe("foo").take(1)(0).getLong(0) must beEqualTo(7l)
  }

  "Not run commands" >>
  {
    Fragment.foreach(Seq(
      "Insert" -> "INSERT INTO foo VALUES (1,1), (2,2);",
      "Create Table" -> "CREATE TABLE foo(a int, b int);",
      "Update" -> "UPDATE foo SET a = b * 2;",
      "Alter" -> "ALTER TABLE foo ADD COLUMN A int;",
    )) { case (label, query) =>
      label >> {
        val workflow = MutableProject("Not run commands")
        workflow.sql(query -> null, waitForResult = false) 
        workflow.waitUntilReadyAndThrowOnError must throwA[RuntimeException]
        workflow.lastOutputString must startWith("Only queries are supported in SQL")
      }
    }
  }
}