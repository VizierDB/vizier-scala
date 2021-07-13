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

import scalikejdbc.DBSession
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog._
import info.vizierdb.catalog.serialized._

sealed trait WorkflowDelta

case class InsertCell(cell: ModuleDescription, position: Int) extends WorkflowDelta
object InsertCell { implicit val format: Format[InsertCell] = Json.format }

case class UpdateCell(cell: ModuleDescription, position: Int) extends WorkflowDelta
object UpdateCell { implicit val format: Format[UpdateCell] = Json.format }

case class DeleteCell(position: Int) extends WorkflowDelta
object DeleteCell { implicit val format: Format[DeleteCell] = Json.format }

case class UpdateCellState(position: Int, state: ExecutionState.T) extends WorkflowDelta
object UpdateCellState { implicit val format: Format[UpdateCellState] = Json.format }

case class AppendCellMessage(position: Int, stream: StreamType.T, message: MessageDescription) extends WorkflowDelta
object AppendCellMessage { implicit val format: Format[AppendCellMessage] = Json.format }

case class AppendCellArtifact(position: Int, name: String, artifactId: String, artifactType: ArtifactType.T) extends WorkflowDelta
object AppendCellArtifact { implicit val format: Format[AppendCellArtifact] = Json.format }

case class DeleteCellArtifact(position: Int, name: String) extends WorkflowDelta
object DeleteCellArtifact { implicit val format: Format[DeleteCellArtifact] = Json.format }

case class AdvanceResultId(position: Int, resultId: String) extends WorkflowDelta
object AdvanceResultId { implicit val format: Format[AdvanceResultId] = Json.format }


object WorkflowDelta
{
  val OP_TYPE = "operation"

  val INSERT_CELL          = "insert_cell"
  val UPDATE_CELL          = "update_cell"
  val DELETE_CELL          = "delete_cell"
  val UPDATE_CELL_STATE    = "update_cell_state"
  val APPEND_CELL_MESSAGE  = "append_cell_message"
  val APPEND_CELL_ARTIFACT = "append_cell_artifact"
  val DELETE_CELL_ARTIFACT = "delete_cell_artifact"
  val ADVANCE_RESULT_ID    = "advance_result_id"

  implicit val format: Format[WorkflowDelta] = Format(
    new Reads[WorkflowDelta]() {
      def reads(j: JsValue): JsResult[WorkflowDelta] =
        (j \ OP_TYPE).as[String] match {
          case INSERT_CELL          => JsSuccess(j.as[InsertCell])
          case UPDATE_CELL          => JsSuccess(j.as[UpdateCell])
          case DELETE_CELL          => JsSuccess(j.as[DeleteCell])
          case UPDATE_CELL_STATE    => JsSuccess(j.as[UpdateCellState])
          case APPEND_CELL_MESSAGE  => JsSuccess(j.as[AppendCellMessage])
          case APPEND_CELL_ARTIFACT => JsSuccess(j.as[AppendCellArtifact])
          case DELETE_CELL_ARTIFACT => JsSuccess(j.as[DeleteCellArtifact])
          case ADVANCE_RESULT_ID    => JsSuccess(j.as[AdvanceResultId])
          case _ => JsError()
        }
    },
    new Writes[WorkflowDelta]() {
      def writes(d: WorkflowDelta): JsValue =
        d match { 
          case x:InsertCell         => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(INSERT_CELL))
          case x:UpdateCell         => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(UPDATE_CELL))
          case x:DeleteCell         => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(DELETE_CELL))
          case x:UpdateCellState    => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(UPDATE_CELL_STATE))
          case x:AppendCellMessage  => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(APPEND_CELL_MESSAGE))
          case x:AppendCellArtifact => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(APPEND_CELL_ARTIFACT))
          case x:DeleteCellArtifact => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(DELETE_CELL_ARTIFACT))
          case x:AdvanceResultId    => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(ADVANCE_RESULT_ID))
        }
    }
  )
}