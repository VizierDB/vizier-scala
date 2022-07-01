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
import info.vizierdb.serializers._
import info.vizierdb.delta.WorkflowDelta
import info.vizierdb.api.websocket.NotificationWebsocketMessage
import info.vizierdb.api.websocket.WebsocketResponse

trait TestFixtures
{
  implicit val ctx = Ctx.Owner.Unsafe

  val project = new Project(1, autosubscribe = false)
  project.branchSubscription = Some(MockBranchSubscription)
  project.workflow() = Some(new Workflow(MockBranchSubscription, project))
  def workflow = project.workflow.now.get
  def modules = workflow.moduleViewsWithEdits

  def init(workflow: serialized.WorkflowDescription = TestFixtures.defaultWorkflow) =
  {
    MockBranchSubscription.onSync(workflow)
  }

  def prependTentative(): TentativeModule =
    modules.prependTentative(Some(TestFixtures.defaultPackages))

  def appendTentative(): TentativeModule =
    modules.appendTentative(Some(TestFixtures.defaultPackages))

  def insertTentativeAfter(element: WorkflowElement): TentativeModule =
    modules.insertTentativeAfter(element, Some(TestFixtures.defaultPackages))

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
      (requestPath, requestArgs) => {
        request = requestPath
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
    extends BranchSubscription(project, 1)
  {

    val expectedMessages = mutable.Stack[(Seq[String], Map[String, JsValue]) => JsValue]()

    override def getSocket(): dom.WebSocket =
    {
      return null
    }

    override def makeRequest(leafPath: Seq[String], args: Map[String, JsValue]): Future[JsValue] =
    {
      assert(expectedMessages.size > 0, "Unexpected message sent")
      val handleRequest = expectedMessages.pop()
      return MockPromise(handleRequest(leafPath, args)).future
    }
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
      artifacts = Seq(),
      readOnly = false,
      createdAt = new js.Date(),
      action = "create",
      actionModule = None,
      packageId = None,
      commandId = None
    )
}