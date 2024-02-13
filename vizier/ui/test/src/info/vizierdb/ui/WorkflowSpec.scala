/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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
import info.vizierdb.types._
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
        TestFixtures.command("debug", "add")
      )
      assert(editor.activeView.now.get.isRight)
      val module = editor.activeView.now.get.right.get
      module.setState(
        "output" -> JsString("foo")
      )
      assert(
        module.currentState
              .find { _.id.equals("output") }
              .get
              .value
              .as[String]
              .equals("foo"),
      )


      val derivedModule =
        BuildA.Module(
          packageId = "debug",
          commandId = "add",
          id = 999999,
          artifacts = Seq(
            "foo" -> ArtifactType.DATASET
          )
        )("output" -> JsString("foo"))

      val (requestPath, requestArgs, _) = 
        pushResponse { 
          BuildA.WorkflowByAppending(
            TestFixtures.defaultWorkflow.copy(modules = Seq.empty), 
            derivedModule
          )
        } {
          module.saveState()
        }
      assert(requestPath.last.equals("workflowAppend"))
      assert(requestArgs("packageId").as[String].equals("debug"))
      assert(requestArgs("commandId").as[String].equals("add"))
      assert(requestArgs("arguments").as[CommandArgumentList.T]
                    .find { _.id.equals("output") }
                    .isDefined)

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

      // println(modules.mkString(", "))

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

      // println(modules.mkString(", "))
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
      assert(
        modules.allArtifacts.now.contains("foo")
      )
    }

    test("Insert in the middle") {
      // test preconditions
      assert(modules.find { _.isInstanceOf[TentativeModule] }.isEmpty)
      assert(modules.size >= 2)

      // println(modules.mkString(", "))

      val editor = insertTentativeAfter(modules.first)
      editor.selectCommand("debug", 
        TestFixtures.command("debug", "drop")
      )
      val module = editor.activeView.now.get.right.get
      modules.first.validate()
      
      assert(modules.find { _.isInstanceOf[TentativeModule] }.isDefined)

      val derivedModule =
        BuildA.Module(
          packageId = "debug",
          commandId = "drop",
          id = 888888
        )("dataset" -> JsString("foo"))
      val (requestPath, requestArgs, _) = 
        pushResponse { 
          BuildA.WorkflowByInserting(
            TestFixtures.defaultWorkflow.copy(
              modules = modules.baseElements.toSeq.map { _.description }
            ),
            1,
            derivedModule
          )
        } {
          module.saveState()
        }

      Logging.trace(
        // "info.vizierdb.ui.components.TentativeEdits"
      ) {
        signalDelta(
          delta.InsertCell(
            position = 1,
            cell = derivedModule
          )
        )
      }
      // println(modules.mkString(", "))
      // println(modules.baseElements.mkString(", "))

      // println(modules(1))
      // println(modules.baseElements(1))

      assert(modules(1) eq modules.baseElements(1))
      assert(modules.find { _.isInstanceOf[TentativeModule] }.isEmpty)

      assert(
        modules.allArtifacts.now.contains("foo")
      )

    }


    test("Safe Deletions") {
      Logging.trace(
        // "info.vizierdb.ui.components.TentativeEdits"
        // "info.vizierdb.ui.components.Module",
        // "info.vizierdb.ui.components.TentativeModule",
        // "info.vizierdb.ui.components.TentativeEdits$Tail$"
      ) {
        var loopBlocker = 0
        // println(modules.mkString(", "))
        while(modules.size < 5 && loopBlocker < 5){
          // println(s"Modules: ${modules.size}; ${modules.mkString(", ")}")
          signalDelta(
            delta.InsertCell(
              cell = 
                BuildA.Module(
                  packageId = "debug",
                  commandId = "dummy",
                )("dataset" -> JsString("foo")),
              position = modules.baseElements.size,
            )
          )
          loopBlocker += 1
        }
        assert(
          modules.allArtifacts.now.contains("foo")
        )
      }


      Logging.trace(
        // "info.vizierdb.ui.components.TentativeEdits"
        // "info.vizierdb.ui.components.Module",
        // "info.vizierdb.ui.components.TentativeModule",
        // "info.vizierdb.ui.components.TentativeEdits$Tail$"
      ) {
        // println(modules.mkString(", "))
        val target = modules.baseElements(1)
        modules.first.validate()
        signalDelta(
          delta.DeleteCell(1)
        )

        // println(modules.mkString(", "))

        modules.first.validate()
        assert(modules.find { _ eq target }.isEmpty)
        assert(
          modules.allArtifacts.now.contains("foo")
        )

      }

      Logging.trace(
        // "info.vizierdb.ui.components.TentativeEdits"
        // "info.vizierdb.ui.components.Module",
        // "info.vizierdb.ui.components.TentativeModule",
        // "info.vizierdb.ui.components.TentativeEdits$Tail$"
      ) {
        // println(modules.mkString(", "))
        val target = modules.baseElements(modules.baseElements.size-1)
        // println(target)
        modules.first.validate()
        signalDelta(
          delta.DeleteCell(modules.baseElements.size-1)
        )

        // println(modules.mkString(", "))

        modules.first.validate()
        assert(modules.find { _ eq target }.isEmpty)

      }

    }


    test("Double-updates") {
      Logging.trace(
        //
      ) {
        // println(modules.zipWithIndex.mkString("\n"))
        assert(modules.baseElements.size >= 2) 
        signalDelta(
          delta.UpdateCell(
            cell = 
              BuildA.Module(
                packageId = "debug",
                commandId = "dummy",
              )("dataset" -> JsString("shazbot")),
            position = 1
          )
        )
        // println("------")
        // println(modules.zipWithIndex.mkString("\n"))
        assert(modules(1).isInstanceOf[Module])
        assert(modules(1).asInstanceOf[Module]
                         .subscription
                         .arguments.now
                         .filter { _.id == "dataset" }
                         .map { _.value.as[String] }
                         .contains("shazbot"))
        signalDelta(
          delta.UpdateCell(
            cell = 
              BuildA.Module(
                packageId = "debug",
                commandId = "dummy",
              )("dataset" -> JsString("cool cool cool")),
            position = 1
          )
        )
        assert(modules(1).isInstanceOf[Module])
        assert(modules(1).asInstanceOf[Module]
                         .subscription
                         .arguments.now
                         .filter { _.id == "dataset" }
                         .map { _.value.as[String] }
                         .contains("cool cool cool"))
      }
    }
  }

}