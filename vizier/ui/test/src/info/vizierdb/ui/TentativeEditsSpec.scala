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
package info.vizierdb.ui

import rx._
import utest._
import info.vizierdb.ui.components.{ TentativeEdits, Module }
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.test._
import info.vizierdb.types._
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.ui.components.StaticWorkflow


object TentativeEditsSpec extends TestSuite with TestFixtures
{
  val base = RxBuffer[Module]()
  val edits = new TentativeEdits(base, null, null)



  val tests = Tests 
  {
    test("Inserts into buffer go where they're needed") {
      implicit val ctx = Ctx.Owner.safe() 

      base.append(
        new Module(new ModuleSubscription(
          BuildA.Module(
            packageId = "debug",
            commandId = "add",
            id = 1,
            artifacts = Seq(
              "foo" -> ArtifactType.DATASET
            )
          )(),
          Right(null),
          Var(0)
        ))
      )
      base.append(
        new Module(new ModuleSubscription(
          BuildA.Module(
            packageId = "debug",
            commandId = "add",
            id = 2,
            artifacts = Seq(
              "foo" -> ArtifactType.DATASET
            )
          )(),
          Right(null),
          Var(1)
        ))
      )
      base.insert(
        1,
        new Module(new ModuleSubscription(
          BuildA.Module(
            packageId = "debug",
            commandId = "add",
            id = 3,
            artifacts = Seq(
              "foo" -> ArtifactType.DATASET
            )
          )(),
          Right(null),
          Var(0)
        ))
      )
      assert(base.size == 3)
      assert(edits.size == 3)
      val ids = edits.iterator.map { case m:Module => m.id }.toIndexedSeq
      assert(ids == Seq(1, 3, 2))
    }
  }
}