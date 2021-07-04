package info.vizierdb.test

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.JSON
import rx._
import org.scalajs.dom
import info.vizierdb.types._
import info.vizierdb.ui.components._
import info.vizierdb.ui.network._
import info.vizierdb.ui._
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

  def appendModule(): TentativeModule =
  {
    modules.appendTentative(Some(TestFixtures.defaultPackages))
    val Right(ret) = modules.last
    return ret
  }

  def insertModule(n: Int): TentativeModule =
  {
    modules.insertTentative(n, Some(TestFixtures.defaultPackages))
    val Right(ret) = modules(n)
    return ret
  }


  def expectMessage[T](response: Any)(op: => T): (js.Dynamic, T) =
  {
    var request: js.Dynamic = null
    val height = MockBranchSubscription.expectedMessages.size
    MockBranchSubscription.expectedMessages.push( 
      actualRequest => {
        request = actualRequest
        response.asInstanceOf[js.Dynamic]
      }
    )
    val ret = op
    assert(MockBranchSubscription.expectedMessages.size == height, 
          s"Expecting ${Math.abs(MockBranchSubscription.expectedMessages.size - height)} ${if(MockBranchSubscription.expectedMessages.size > height){ "fewer" } else { "more" }} messages than were sent."
        )
    return (request, ret)
  }

  object MockBranchSubscription
    extends BranchSubscription("1", "1", Vizier.api)
  {

    val expectedMessages = mutable.Stack[js.Dynamic => js.Dynamic]()

    override def getSocket(): dom.WebSocket =
    {
      return null
    }

    override def withResponse(arguments: (String, Any)*): 
      Promise[js.Dynamic] =
    {
      assert(expectedMessages.size > 0, "Unexpected message sent")
      val handleRequest = expectedMessages.pop()
      MockPromise(
        handleRequest(js.Dictionary(arguments:_*).asInstanceOf[js.Dynamic])
      )
    }
  }

  object MockAPI
    extends API("")
  {
    override def packages(): Future[Seq[PackageDescriptor]] =
      MockFuture(TestFixtures.defaultPackages)

    override def project(projectId: Identifier): Future[ProjectDescription] =
      ???

    override def branch(projectId: Identifier, branchId: Identifier): Future[BranchDescription] =
      ???
  }
}

object TestFixtures
{

  val defaultPackages: Seq[PackageDescriptor] = 
    Seq(
      BuildA.Package("debug")(
        BuildA.Command("add")(
          BuildA.Parameter("output", "string")
        ),
        BuildA.Command("drop")(
          BuildA.Parameter("dataset", "string")
        )
      )
    )

  def command(packageId: String, commandId: String) =
    defaultPackages
      .find(_.id.equals(packageId))
      .get
      .commands
      .find { _.id.equals(commandId) }
      .get

  val defaultWorkflow = 
    js.Dictionary(
      "state" -> ExecutionState.DONE,
      "modules" -> Seq(
        BuildA.Module(
          "debug", "add", 
          artifacts = Seq("foo" -> ArtifactType.DATASET)
        )( 
          "output" -> "foo"
        )
      ),
      "datasets" -> Seq(),
      "dataobjects" -> Seq(),
      "readOnly" -> false,
      "tableOfContents" -> Seq()
    ).asInstanceOf[WorkflowDescription]
}