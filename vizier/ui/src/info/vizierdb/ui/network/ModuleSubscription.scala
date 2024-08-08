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
package info.vizierdb.ui.network

import rx._
import info.vizierdb.ui.rxExtras.{ RxBufferVar, RxBuffer, RxBufferView }
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.types._
import info.vizierdb.util.Logging
import info.vizierdb.serialized
import info.vizierdb.serializers._
import info.vizierdb.ui.components.Module
import info.vizierdb.ui.components.TentativeEdits
import info.vizierdb.ui.components.Workflow
import info.vizierdb.ui.components.StaticWorkflow


class ModuleSubscription(
  initial: serialized.ModuleDescription, 
  val branch: Either[BranchSubscription, StaticWorkflow],
  val position: Var[Int]
)
  extends Object
  with Logging
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  var id: Identifier = initial.moduleId
  val state = Var(initial.statev2)
  val commandId = initial.command.commandId
  val packageId = initial.command.packageId
  val arguments = Var(initial.command.arguments)
  lazy val text = Var(initial.text)
  val timestamps = Var(initial.timestamps)
  def toc = initial.toc
  val inputs = Var[Map[String,Identifier]](
    initial.inputs
  )
  val outputs = Var[Map[String,Option[serialized.ArtifactSummary]]](
    initial.artifacts.map { x => x.name -> Some(x) }.toMap
  )
  val messages:RxBufferVar[serialized.MessageDescriptionWithStream] = 
    RxBuffer[serialized.MessageDescriptionWithStream]( (
      initial.outputs.stdout.map { _.withStream(StreamType.STDOUT) } ++
      initial.outputs.stderr.map { _.withStream(StreamType.STDERR) }
    ):_* )
  logger.debug(s"${messages.length} Messages; ${outputs.now.size} outputs; $outputs")

  def description =
    serialized.ModuleDescription(
      id = id.toString,
      moduleId = id,
      state = ExecutionState.translateToClassicVizier(state.now),
      statev2 = state.now,
      command = initial.command,
      text = text.now,
      toc = toc,
      timestamps = timestamps.now,
      artifacts = outputs.now.values.collect { case Some(s) => s }.toSeq,
      deleted = outputs.now.collect { case (k, None) => k }.toSeq,
      inputs = inputs.now,
      outputs = serialized.ModuleOutputDescription(
        stdout = messages.filter { _.stream == StreamType.STDOUT }
                         .map { _.removeType },
        stderr = messages.filter { _.stream == StreamType.STDERR }
                         .map { _.removeType }
      ),
      resultId = initial.resultId
    )

  def isEditable = branch.isLeft

  def client = 
    branch match { 
      case Left(l) => l.Client 
      case Right(_) => throw new IllegalArgumentException("Trying to edit a static module")
    }

  /**
   * Delete this module from the workflow
   */
  def delete(): Unit = client.workflowDelete(position.now)

  /**
   * Freeze the current cell
   */
  def freezeCell(): Unit = client.workflowFreezeOne(position.now)

  /**
   * Freeze all cells starting with current cell
   */
  def freezeFrom(): Unit = client.workflowFreezeFrom(position.now)

  /**
   * Thaw the current cell
   */
  def thawCell(): Unit = client.workflowThawOne(position.now)

  /**
   * Thaw all cells upto current cell
   */
  def thawUpto(): Unit = client.workflowThawUpto(position.now)

  /**
   * Rerun the present cell
   */
  def reRunModule(): Unit = 
  {
    client.workflowReplace(
      modulePosition = position.now,
      packageId = packageId,
      commandId = commandId,
      arguments = arguments.now
    )
  }

}