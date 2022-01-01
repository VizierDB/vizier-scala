package info.vizierdb.test

import scala.collection.mutable
import scala.scalajs.js
import play.api.libs.json._
import rx._
import org.scalajs.dom
import info.vizierdb.types._
import info.vizierdb.ui.components._
import info.vizierdb.ui.network._
import info.vizierdb.ui._
import scala.concurrent.{ Promise, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.serialized
import info.vizierdb.serialized.SimpleParameterDescription
import info.vizierdb.shared.HATEOAS
import info.vizierdb.serializers._
import info.vizierdb.delta.WorkflowDelta
import info.vizierdb.api.websocket.NotificationWebsocketMessage
import info.vizierdb.api.websocket.WebsocketResponse

trait TestFixtures
{
  implicit val ctx = Ctx.Owner.Unsafe

  val project = new Project(1, MockAPI, autosubscribe = false)
  project.branchSubscription = Some(MockBranchSubscription)
  project.workflow() = Some(new Workflow(MockBranchSubscription, project))
  def workflow = project.workflow.now.get
  def modules = workflow.moduleViewsWithEdits

  def init(workflow: serialized.WorkflowDescription = TestFixtures.defaultWorkflow) =
  {
    MockBranchSubscription.onSync(workflow)
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

  def signalDelta(delta: WorkflowDelta) =
  {
    implicit val format = MockBranchSubscription.websocketResponseFormat
    MockBranchSubscription.onMessage(js.Dictionary(
      "data" -> Json.toJson(NotificationWebsocketMessage(delta):WebsocketResponse).toString
    ).asInstanceOf[dom.MessageEvent])
  }


  def pushResponse[T, M](response: M)(op: => T)(implicit writes: Writes[M]): (Seq[String], T) =
  {
    var request: Seq[String] = null
    val height = MockBranchSubscription.expectedMessages.size
    MockBranchSubscription.expectedMessages.push( 
      actualRequest => {
        request = actualRequest
        Json.toJson(response)
      }
    )
    val ret = op
    assert(MockBranchSubscription.expectedMessages.size == height, 
          s"Expecting ${Math.abs(MockBranchSubscription.expectedMessages.size - height)} ${if(MockBranchSubscription.expectedMessages.size > height){ "fewer" } else { "more" }} messages than were sent."
        )
    return (request, ret)
  }



  object MockBranchSubscription
    extends BranchSubscription(1, 1, Vizier.api)
  {

    val expectedMessages = mutable.Stack[Seq[String] => JsValue]()

    override def getSocket(): dom.WebSocket =
    {
      return null
    }

    def makeRequest(request: Seq[String]): Promise[JsValue] =
    {
      assert(expectedMessages.size > 0, "Unexpected message sent")
      val handleRequest = expectedMessages.pop()
      return MockPromise(handleRequest(request))
    }
  }

  object MockAPI
    extends API("")
  {
    override def packages(): Future[Seq[serialized.PackageDescription]] =
      MockFuture(TestFixtures.defaultPackages)

    override def project(projectId: Identifier): Future[serialized.ProjectDescription] =
      ???

    override def branch(projectId: Identifier, branchId: Identifier): Future[serialized.BranchDescription] =
      ???
  }
}

object TestFixtures
{

  val defaultPackages: Seq[serialized.PackageDescription] = 
    Seq(
      BuildA.Package("debug")(
        BuildA.Command("add")(
          SimpleParameterDescription("output", "Output", "string", false, false, None, _, None)
        ),
        BuildA.Command("drop")(
          SimpleParameterDescription("dataset", "Dataset", "string", false, false, None, _, None)
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
    serialized.WorkflowDescription(
      id = 1,
      state = ExecutionState.translateToClassicVizier(ExecutionState.DONE),
      statev2 = ExecutionState.DONE,
      modules = Seq(
        BuildA.Module(
          "debug", "add", 
          artifacts = Seq("foo" -> ArtifactType.DATASET)
        )( 
          "output" -> JsString("foo")
        )
      ),
      datasets = Seq(),
      dataobjects = Seq(),
      readOnly = false,
      tableOfContents = None,
      createdAt = new js.Date(),
      action = "create",
      actionModule = None,
      packageId = None,
      commandId = None,
      links = HATEOAS()
    )
}