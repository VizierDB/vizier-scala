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
package info.vizierdb.viztrails

import scalikejdbc._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import play.api.libs.json._

import info.vizierdb.Vizier
import info.vizierdb.types._
import info.vizierdb.catalog._
import info.vizierdb.test.SharedTestResources
import org.slf4j.LoggerFactory

class SchedulerSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  lazy val projectId = 
    DB.autoCommit { implicit s => 
      Project.create("Executor Test").id
    }
  def project(implicit session: DBSession) = Project.get(projectId)
  def activeBranch(implicit session: DBSession) = Project.activeBranchFor(projectId)
  def activeHead(implicit session: DBSession) = Project.activeHeadFor(projectId)

  def append(packageId: String, commandId: String)(args: (String,Any)*): Workflow = 
  {
    val workflow = 
      DB autoCommit { implicit s =>
        activeBranch.append(packageId, commandId)(args:_*)._2
      }
    // The workflow must be scheduled AFTER the enclosing transaction finishes
    Scheduler.schedule(workflow.id)
    return workflow
  }

  def insert(position: Int, packageId: String, commandId: String)(args: (String,Any)*): Workflow = 
  {
    val workflow = 
      DB autoCommit { implicit s =>
        activeBranch.insert(position, packageId, commandId)(args:_*)._2
      }
    // The workflow must be scheduled AFTER the enclosing transaction finishes
    Scheduler.schedule(workflow.id)
    return workflow
  }
  def testCells[T](op: (Seq[Cell] => T)): T =
    op(DB.readOnly { implicit s => activeHead.cellsInOrder }.toSeq )
  def testModules[T](op: (Seq[Module] => T)): T =
    op(DB.readOnly { implicit s => activeHead.modulesInOrder }.toSeq )
  def testLogs[T](op: (Seq[Seq[String]] => T)): T =
    op(DB.readOnly { implicit s => activeHead.cellsInOrder.map { _.messages } }
        .map { _.map { _.dataString}.toSeq }.toSeq
      )
  def waitUntilDone() =
    Scheduler.joinWorkflow( DB.readOnly { implicit s => activeHead }.id )

  "execute a test workflow" >> {

    // Force project creation
    projectId

    // Create a branch and populate it with a single DUMMY.PRINT("Hello World") cell
    append("dummy","print")("value" -> "Hello World")
    waitUntilDone()
    val firstWorkflowId = DB readOnly { implicit s => activeBranch.headId }

    // Check the results of the workflow.  Save the result id of the one cell
    testLogs { logs => 
      logs must haveSize(1)
      logs(0) must contain("Hello World") 
    }
    val firstResultId = DB readOnly { implicit s => activeHead.cells.head.resultId.get }

    // Add another DUMMY.PRINT module prepending the first one.
    insert(0, "dummy","print")("value" -> "Hello Time Travel")

    testModules { modules => 
      modules.map { _.arguments.as[JsObject].values.head.as[String] }
    } must beEqualTo(Seq("Hello Time Travel", "Hello World"))

    // Process the workflow
    waitUntilDone()
    
    // Simple sanity check.  Expecting to see a version bump on the workflow
    DB readOnly { implicit s =>  
      activeHead.id must not(beEqualTo(firstWorkflowId))
    }
    // Check the results of the workflow.   
    testLogs { logs =>
      logs must haveSize(2)
      // The second cell had better produce the right output
      logs(0) must contain("Hello Time Travel")
    }
    testCells { cells => 
      // And we should not have re-executed the earlier cell (even though it follows the new one)
      cells(1).resultId must beEqualTo(Some(firstResultId))
    }

    // Create a datset and consume it
    insert(1, "dummy","create")(
      "dataset"    -> "testData",
      "content"    -> "🙋"
    )

    append("dummy","consume")("datasets"    -> Seq("testData"))
    waitUntilDone()

    testLogs { logs => 
      logs must haveSize(4)
      logs(3) must contain("🙋")
    }
  }

  "work with frozen cells" >> 
  {
    val project = MutableProject("Frozen Cell Test")

    project.append("dummy", "create")(
      "dataset" -> "test",
      "content" -> "🎆"
    )

    project.append("dummy", "consume")(
      "datasets" -> Seq(
        Map("dataset" -> "test")
      )
    )

    project.waitUntilReadyAndThrowOnError
    project.lastOutputString must beEqualTo("🎆")

    project.freezeFrom(1)
    project.waitUntilReadyAndThrowOnError
    project.lastOutputString must beEqualTo("🎆")

    project.insert(1, "dummy", "create")(
      "dataset" -> "test",
      "content" -> "👎"
    )

    project.insert(2, "dummy", "consume")(
      "datasets" -> Seq(
        Map("dataset" -> "test")
      )
    )

    project.waitUntilReadyAndThrowOnError
    project(2).get.map { _.dataString }.mkString must beEqualTo("👎")
    project(3).get.map { _.dataString }.mkString must beEqualTo("🎆")

    project.thawUpto(3)
    project.waitUntilReadyAndThrowOnError
    project(3).get.map { _.dataString }.mkString must beEqualTo("👎")
  }
}

