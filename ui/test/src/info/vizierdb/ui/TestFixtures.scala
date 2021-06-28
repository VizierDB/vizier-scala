package info.vizierdb.ui

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.JSON
import rx._
import org.scalajs.dom
import info.vizierdb.types._
import info.vizierdb.ui.components._
import info.vizierdb.ui.network._
import scala.concurrent.{ Promise, Future }
import scala.concurrent.ExecutionContext.Implicits.global


trait TestFixtures
{
  implicit val ctx = Ctx.Owner.Unsafe

  val project = new Project("1", MockAPI, autosubscribe = false)
  project.branchSubscription = Some(MockBranchSubscription)
  project.workflow() = Some(new Workflow(MockBranchSubscription, project))
  def workflow = project.workflow.now.get
  def modules = workflow.moduleViewsWithEdits

  def init(workflow: WorkflowDescription = TestFixtures.defaultWorkflow) =
  {
    sendMessage(workflow)
  }

  def sendMessage(message: js.Any) =
  {
    MockBranchSubscription.onMessage(
      js.Dictionary(
        "data" -> JSON.stringify(message)
      ).asInstanceOf[dom.MessageEvent]
    )
  }



  def expectMessage[T](request: (String, Any)*)(response: Any)(op: => T): T =
  {
    val height = MockBranchSubscription.expectedMessages.size
    MockBranchSubscription.expectedMessages.push( (request, response.asInstanceOf[js.Dynamic]) )
    val ret = op
    assert(MockBranchSubscription.expectedMessages.size == height, 
          s"Expecting ${Math.abs(MockBranchSubscription.expectedMessages.size - height)} ${if(MockBranchSubscription.expectedMessages.size > height){ "fewer" } else { "more" }} messages than were sent."
        )
    return ret
  }

  object MockBranchSubscription
    extends BranchSubscription("1", "1", Vizier.api)
  {

    val expectedMessages = mutable.Stack[(Seq[(String, Any)], js.Dynamic)]()

    override def getSocket(): dom.WebSocket =
    {
      return null
    }

    override def withResponse(arguments: (String, Any)*): 
      Promise[js.Dynamic] =
    {
      assert(expectedMessages.size > 0, "Unexpected message sent")
      val (expectedFields, response) = expectedMessages.pop()
      val fields = arguments.toMap

      val errors = 
        expectedFields.map { case (field, value) =>
                        (field, value, fields(field))
                      }
                      .filter { x => !x._2.equals(x._3) }

      assert(errors.isEmpty,
        s"Error in message.\n${errors.map { case (field, expected, got) => 
                                            s"Field: $field\n  Expected: $expected\n  Got: $got"
                                          }.mkString("\n")}"
      )
      val ret = Promise[js.Dynamic]()
      ret.success(response)
      return ret
    }
  }

  object MockAPI
    extends API("")
  {
    override def packages(): Future[Seq[PackageDescriptor]] =
      Future(Seq())

    override def project(projectId: Identifier): Future[ProjectDescription] =
      ???

    override def branch(projectId: Identifier, branchId: Identifier): Future[BranchDescription] =
      ???
  }
}

object TestFixtures
{
  val defaultWorkflow = 
    js.Dictionary(
      "state" -> ExecutionState.DONE,
      "modules" -> Seq(
        js.Dictionary(
          "id" -> "raw",
          "state" -> -1,
          "statev2" -> ExecutionState.DONE.id,
          "command" -> js.Dictionary(

          ).asInstanceOf[CommandDescriptor],
          "text" -> "LOAD DATASET foo;",
          "links" -> js.Dictionary(),
          "outputs" -> Seq(),
          "artifacts" -> Seq(
            js.Dictionary(
              "id" -> 23,
              "name" -> "foo",
              "category" -> ArtifactType.DATASET.toString,
              "objType" -> "dataset/view"

            ).asInstanceOf[ArtifactSummary]
          )
        ).asInstanceOf[ModuleDescription]
      ),
      "datasets" -> Seq(),
      "dataobjects" -> Seq(),
      "readOnly" -> false,
      "tableOfContents" -> Seq()
    ).asInstanceOf[WorkflowDescription]
}