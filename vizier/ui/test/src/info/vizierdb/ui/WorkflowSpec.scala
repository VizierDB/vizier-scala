package info.vizierdb.ui

import utest._

import play.api.libs.json._
import scala.scalajs.js
import info.vizierdb.test._
import info.vizierdb.ui.components._
import info.vizierdb.ui.network._
import scala.concurrent.Await
import info.vizierdb.serializers._
import info.vizierdb.serialized.CommandArgumentList
import info.vizierdb.delta

object WorkflowSpec extends TestSuite with TestFixtures
{
  val tests = Tests 
  {
    test("Cancel edits") {

      val initialSize = modules.size 
      appendModule()

      assert(modules.last.isRight)
      val Right(editor) = modules.elements.last
      editor.cancelSelectCommand()

      assert(modules.size == initialSize)

    }

    test("Basic Edit Flow") {

      val initialSize = modules.size 
      val editor = appendModule()
      editor.selectCommand("debug", 
        TestFixtures.command("debug", "drop")
      )
      assert(editor.activeView.now.get.isRight)
      val module = editor.activeView.now.get.right.get
      module.setState(
        "dataset" -> JsString("foo")
      )
      assert(
        module.currentState
              .find { _.id.equals("dataset") }
              .get
              .value
              .as[String]
              .equals("foo")
      )

      val derivedModule =
        BuildA.Module(
          packageId = "debug",
          commandId = "drop",
          id = 999999
        )("dataset" -> JsString("foo"))

      val (request, _) = 
        pushResponse { 
          BuildA.WorkflowByAppending(TestFixtures.defaultWorkflow.copy(modules = Seq.empty), derivedModule)
        } {
          module.saveState()
        }
      // assert(request.path.last.equals("workflowInsert"))
      // assert(request.args("packageId").as[String].equals("debug"))
      // assert(request.args("commandId").as[String].equals("drop"))
      // assert(request.args("arguments").as[CommandArgumentList.T]
      //               .find { _.id.equals("dataset") }
      //               .isDefined)

      assert(modules.size == initialSize + 1)

      signalDelta(
        delta.InsertCell(
          position = initialSize,
          cell = derivedModule
        )
      )
      assert(modules.size == initialSize + 1)
      assert(modules.last.isLeft)
    }

  }
}