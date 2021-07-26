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
package info.vizierdb.delta

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import org.mimirdb.vizual

class ComputeDeltaSpec 
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  "Compute deltas" >> {
    val project = MutableProject("Basic Deltas")

    project.load("test_data/r.csv", "r", waitForResult = false)
    val snapshot1 = project.snapshot
    project.waitUntilReadyAndThrowOnError
    val snapshot2 = project.snapshot

    ComputeDelta(snapshot1).map { _ must beAnInstanceOf[UpdateCell] }

    project.vizual("r", 
      vizual.InsertColumn(None, "D", None)
    )
    ComputeDelta(snapshot2).map { _ must beAnInstanceOf[InsertCell] }

    project.insert(1, "dummy", "print")("value" -> "hi 1!")
    project.insert(1, "dummy", "print")("value" -> "hi 2!")
    project.waitUntilReady

    val snapshot3 = project.snapshot
    ComputeDelta(snapshot1)
      .foldLeft(snapshot1) { _.applyDelta(_) }
      .withWorkflowId(snapshot3.workflowId) must beEqualTo(snapshot3)
  }

}