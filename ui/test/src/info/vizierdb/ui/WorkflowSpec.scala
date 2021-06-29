package info.vizierdb.ui

import utest._

import scala.scalajs.js
import info.vizierdb.test._
import info.vizierdb.ui.components._
import scala.concurrent.Await

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
        "dataset" -> "foo"
      )
      assert(
        module.arguments
              .find { _.id.equals("dataset") }
              .map { _.value }
              .equals(Some("foo"))
      )
      val (request, _) = 
        expectMessage(
          js.Dictionary("id" -> "appended_module")
        ) {
          module.saveState()
        }
      assert(request.operation.equals("workflow.append"))
      assert(request.packageId.equals("debug"))
      assert(request.commandId.equals("drop"))

      assert(modules.size == initialSize + 1)

      sendMessage(
        js.Dictionary(
          "operation" -> "insert_cell",
          "position" -> initialSize,
          "cell" -> BuildA.Module(
            "debug", "drop",
            id = "appended_module"
          )(
            "dataset" -> "foo"
          ),
        )
      )
      assert(modules.size == initialSize + 1)
      assert(modules.last.isLeft)
    }

  }
}