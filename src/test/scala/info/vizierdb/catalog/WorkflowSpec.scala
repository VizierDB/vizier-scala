/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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

import scalikejdbc._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.viztrails.MutableProject


class WorkflowSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "enumerate workflows" >> {
    val project = MutableProject("Data Project")

    project.append("dummy", "print")(
      "value" -> "thingie1"
    )
    project.append("dummy", "print")(
      "value" -> "thingie2"
    )
    project.waitUntilReady

    val head = project.head

    val cellsAndModules = 
      DB.readOnly { implicit s => head.cellsAndModulesInOrder }

    println(cellsAndModules.map { x => x._1.toString + " / " + x._2.toString }.mkString("\n"))
    cellsAndModules must haveSize(2)
  }
}

