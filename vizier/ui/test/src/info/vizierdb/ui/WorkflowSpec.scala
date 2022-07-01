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
import info.vizierdb.util.Logging
import scala.reflect.ClassTag

object WorkflowSpec extends TestSuite with TestFixtures
{
  val tests = Tests 
  {
    test("Cancel edits") {

      val initialSize = modules.size 
      prependTentative()

      assert(modules.last.isInstanceOf[TentativeModule])
      modules.last.asInstanceOf[TentativeModule].cancelSelectCommand()

      assert(modules.size == initialSize)

    }

    test("Basic Edit Flow") {

      val initialSize = modules.size 
      val editor = appendTentative()
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
              .equals("foo"),
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

      Logging.debug(
        // "info.vizierdb.ui.components.TentativeEdits"
      ) {
        signalDelta(
          delta.InsertCell(
            position = initialSize,
            cell = derivedModule
          )
        )
      }
      modules.first.validate()
      assert(modules.size == initialSize + 1)
      assert(modules.last.isInstanceOf[Module])

      println(modules.mkString(", "))

      Logging.debug(
        // "info.vizierdb.ui.components.TentativeEdits"
      ) {
        signalDelta(
          delta.InsertCell(
            position = initialSize,
            cell = 
              BuildA.Module(
                packageId = "debug",
                commandId = "dummy",
              )("dataset" -> JsString("foo")),
          )
        )
      }

      println(modules.mkString(", "))
      modules.first.validate()
      assert(modules.size == initialSize + 2)

      for(i <- modules.baseElements)
      {
        assert(modules.find { _ eq i }.isDefined)
      }
      for(i <- modules.collect { case m:Module => m })
      {
        assert(modules.baseElements.find { _ eq i }.isDefined)
      }

    }

    test("Safe Deletions") {
      Logging.debug(
        "info.vizierdb.ui.components.TentativeEdits"
      ) {
        var loopBlocker = 0
        println(modules.mkString(", "))
        while(modules.size < 5 && loopBlocker < 5){
          println(s"Modules: ${modules.size}")
          signalDelta(
            delta.InsertCell(
              cell = 
                BuildA.Module(
                  packageId = "debug",
                  commandId = "dummy",
                )("dataset" -> JsString("foo")),
              position = modules.size,
            )
          )
          loopBlocker += 1
        }

        println(modules.mkString(", "))
        val target = modules.baseElements(2)

        modules.first.validate()
        signalDelta(
          delta.DeleteCell(2)
        )

        println(modules.mkString(", "))

        modules.first.validate()
        assert(modules.find { _ eq target }.isEmpty)

      }

    }
  }

}