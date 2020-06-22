package info.vizierdb.viztrails

import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import org.specs2.specification.AfterAll
import org.squeryl.PrimitiveTypeMode._
import play.api.libs.json._

import info.vizierdb.Vizier
import info.vizierdb.test.SharedTestResources
import specs2.arguments

class SchedulerSpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init


  "execute a test workflow" >> {

    // Create a branch and populate it with a single DUMMY.PRINT("Hello World") cell
    val (branch, firstWorkflowId) =
      transaction {
        val project = Project("Executor Test")
        val branch = project.activeBranch
        branch.append(Module("dummy","print")(
          "value" -> "Hello World"
        ))
        (branch, branch.headId)
      }

    // Process the workflow
    Scheduler.processWorkflowUntilDone(branch.headId)

    // Check the results of the workflow.  Save the result id of the one cell
    val firstResultId = 
      transaction {
        val cells = branch.head.cells.toSeq
        cells must haveSize(1)
        branch.head.length must beEqualTo(1)
        val firstResult = cells.head.result
        firstResult must beSome[Result]
        firstResult.get.consoleOutputs.map { _.dataString } must contain("Hello World") 
        firstResult.get.id
      }

    // Add another DUMMY.PRINT module prepending the first one.
    transaction {
      branch.insert(Module("dummy","print")(
        "value" -> "Hello Time Travel"
      ), 0)
      val arglist = 
        branch.head.modulesInOrder.map { module =>
          module.arguments.as[JsObject].values.head.as[String] 
        }.toSeq
      arglist must beEqualTo(Seq("Hello Time Travel", "Hello World"))
    }

    // Process the workflow
    Scheduler.processWorkflowUntilDone(branch.headId)
    
    // Check the results of the workflow.  
    transaction {
      // Simple sanity check.  Expecting to see a version bump on the workflow
      branch.headId must not(beEqualTo(firstWorkflowId))

      // The second cell had better produce the right output
      val cells = branch.head.cells.toSeq
      cells must haveSize(2)
      val secondResult = cells(0).result
      secondResult must beSome[Result]
      secondResult.get.consoleOutputs.map { _.dataString } must contain("Hello Time Travel")

      // And we should not have re-executed the earlier cell (even though it follows the new one)
      cells(1).resultId must beEqualTo(Some(firstResultId))
    }

    // Create a datset and consume it
    transaction {
      branch.insert(Module("dummy","create")(
        "dataset"    -> "testData",
        "content"    -> "🙋"
      ), 1)
      println(branch.head.cellsInOrder.map { _.state }.mkString("\n"))
      branch.append(Module("dummy","consume")(
        "datasets"    -> Seq("testData")
      ))
      println(branch.head.modulesInOrder.mkString("\n"))
      println(branch.head.cellsInOrder.map { _.state }.mkString("\n"))
    }
    Scheduler.processWorkflowUntilDone(branch.headId)
    transaction {
      val cells = branch.head.cells.toSeq
      cells must haveSize(4)
      val consumeResult = cells(3).result
      consumeResult must beSome[Result]
      consumeResult.get.consoleOutputs.map { _.dataString } must contain("🙋")
    }

  }
}