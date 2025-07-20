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

  if(Vizier.api == null){
    Vizier.api = new API("[BASE_URL]/vizier-db/api/v1")
  }
  if(Vizier.links == null){
    Vizier.links = ClientURLs("[BASE_URL]")
  }

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


  def pushResponse[T, M](response: M)(op: => T)(implicit writes: Writes[M]): (Seq[String], Map[String, JsValue], T) =
  {
    var savedRequestPath: Seq[String] = null
    var savedRequestArgs: Map[String, JsValue] = null
    val height = MockBranchSubscription.expectedMessages.size
    MockBranchSubscription.expectedMessages.push( 
      (requestPath, requestArgs) => {
        savedRequestPath = requestPath
        savedRequestArgs = requestArgs
        Json.toJson(response)
      }
    )
    val ret = op
    assert(MockBranchSubscription.expectedMessages.size == height, 
          s"Expecting ${Math.abs(MockBranchSubscription.expectedMessages.size - height)} ${if(MockBranchSubscription.expectedMessages.size > height){ "fewer" } else { "more" }} messages than were sent."
        )
    return (savedRequestPath, savedRequestArgs, ret)
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
          SimpleParameterDescription("output", "Output", "string", false, false, None, _, None, None)
        ),
        BuildA.Command("drop")(
          SimpleParameterDescription("dataset", "Dataset", "string", false, false, None, _, None, None)
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