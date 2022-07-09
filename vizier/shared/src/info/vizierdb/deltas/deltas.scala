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
package info.vizierdb.delta

import info.vizierdb.types.{ ExecutionState, StreamType, Identifier }
import info.vizierdb.serialized
import info.vizierdb.nativeTypes.JsValue

sealed trait WorkflowDelta

case class InsertCell(cell: serialized.ModuleDescription, position: Int) extends WorkflowDelta
case class UpdateCell(cell: serialized.ModuleDescription, position: Int) extends WorkflowDelta
case class DeleteCell(position: Int) extends WorkflowDelta
case class UpdateCellState(position: Int, state: ExecutionState.T, timestamps: serialized.Timestamps) extends WorkflowDelta
case class UpdateCellArguments(position: Int, arguments: serialized.CommandArgumentList.T, newModuleId: Identifier) extends WorkflowDelta
case class AppendCellMessage(position: Int, stream: StreamType.T, message: serialized.MessageDescription) extends WorkflowDelta
case class DeltaOutputArtifact(artifact: Either[String, serialized.ArtifactSummary]) 
{ 
  def isDeletion = artifact.isLeft
  def name: String = artifact match {
    case Left(n) => n
    case Right(a) => a.name
  }
}
object DeltaOutputArtifact { 
  def fromDeletion(name: String) =
    DeltaOutputArtifact(Left(name))
  def fromArtifact(a:serialized.ArtifactSummary) =
    DeltaOutputArtifact(Right(a))
}
case class UpdateCellOutputs(position: Int, outputs: Seq[DeltaOutputArtifact]) extends WorkflowDelta
case class AdvanceResultId(position: Int, resultId: Identifier) extends WorkflowDelta
case class UpdateBranchProperties(properties: Map[String, JsValue]) extends WorkflowDelta
case class UpdateProjectProperties(properties: Map[String, JsValue]) extends WorkflowDelta


object WorkflowDelta
{
  val OP_TYPE = "operation"

  val INSERT_CELL               = "insert_cell"
  val UPDATE_CELL               = "update_cell"
  val DELETE_CELL               = "delete_cell"
  val UPDATE_CELL_STATE         = "update_cell_state"
  val APPEND_CELL_MESSAGE       = "append_cell_message"
  val UPDATE_CELL_ARGUMENTS     = "update_cell_arguments"
  val UPDATE_CELL_OUTPUTS       = "update_cell_outputs"
  val ADVANCE_RESULT_ID         = "advance_result_id"
  val UPDATE_BRANCH_PROPERTIES  = "update_branch_properties"
  val UPDATE_PROJECT_PROPERTIES = "update_project_properties"
}