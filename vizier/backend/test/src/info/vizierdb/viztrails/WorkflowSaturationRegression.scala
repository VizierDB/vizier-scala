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
import info.vizierdb.Vizier

class WorkflowSaturationRegression
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "Recover from Saturated Queue" >>
  {
    val numThreads = 
      (Vizier.config.supervisorThreads().toInt + 3)

    val projects = 
      (1 until numThreads).map { i => MutableProject(s"Workflow Saturation-$i")}

    for(p <- projects){
      p.script(
        """from time import sleep
          |sleep(1)""".stripMargin, 
        waitForResult = false
      )
    }

    for(p <- projects)
    {
      p.waitUntilReadyAndThrowOnError
    }

    ok
  }
}
