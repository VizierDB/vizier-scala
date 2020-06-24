package info.vizierdb.viztrails

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import play.api.libs.json._

import info.vizierdb.Vizier
import info.vizierdb.test.SharedTestResources
import specs2.arguments

class SchedulerSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init

  lazy val project = Project.create("Executor Test")

  def append(packageId: String, commandId: String)(args: (String,Any)*): Workflow = 
  {
    val workflow = 
      catalogTransaction {
        project.activeBranch
               .append(Module(packageId, commandId)(args:_*))
      }
    workflow.schedule()
    return workflow
  }

  def insert(position: Int, packageId: String, commandId: String)(args: (String,Any)*): Workflow = 
  {
    val workflow = 
      catalogTransaction {
        project.activeBranch
               .insert(Module(packageId, commandId)(args:_*), position)
      }
    // The workflow must be scheduled AFTER the enclosing transaction finishes
    workflow.schedule()
    return workflow
  }
  def testCells[T](op: (Seq[Cell] => T)): T =
    catalogTransaction { op(project.activeBranch.head.cellsInOrder.toSeq) }
  def testLogs[T](op: (Seq[Seq[String]] => T)): T =
    testCells { cells => 
      op(cells.map { _.result.get.logEntries.map { _.dataString }.toSeq }.toSeq)
    }
  def waitUntilDone() =
    catalogTransaction { project.activeBranch.head }.waitUntilDone()

  "execute a test workflow" >> {

    catalogTransaction { project }

    // Create a branch and populate it with a single DUMMY.PRINT("Hello World") cell
    append("dummy","print")("value" -> "Hello World")
    waitUntilDone()
    val firstWorkflowId = catalogTransaction { project.activeBranch.headId }

    // Check the results of the workflow.  Save the result id of the one cell
    testLogs { logs => 
      logs must haveSize(1)
      logs(0) must contain("Hello World") 
    }
    val firstResultId = catalogTransaction { project.activeBranch.head.cells.head.resultId.get }

    // Add another DUMMY.PRINT module prepending the first one.
    insert(0, "dummy","print")("value" -> "Hello Time Travel")

    testCells { cells => 
      val cellargs = 
        cells.map { _.module.arguments.as[JsObject].values.head.as[String] }
      cellargs must beEqualTo(Seq("Hello Time Travel", "Hello World"))
    }

    // Process the workflow
    waitUntilDone()
    
    // Simple sanity check.  Expecting to see a version bump on the workflow
    catalogTransaction {
      project.activeBranch.headId must not(beEqualTo(firstWorkflowId))
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
}