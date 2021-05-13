package info.vizierdb.delta

import scalikejdbc.DBSession
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.catalog._
import info.vizierdb.catalog.serialized._

sealed trait WorkflowDelta
{
  def serialize(workflowId: Identifier, branchId: Identifier)(implicit session: DBSession): SerializedWorkflowDelta
}

case class InsertCell(cell: CellState, position: Int) extends WorkflowDelta
{
  def serialize(workflowId: Identifier, branchId: Identifier)(implicit session: DBSession) = 
    SerializedInsertCell(cell.moduleDescription(workflowId = workflowId, branchId = branchId), position)
}
case class UpdateCell(cell: CellState, position: Int) extends WorkflowDelta
{
  def serialize(workflowId: Identifier, branchId: Identifier)(implicit session: DBSession) = 
    SerializedUpdateCell(cell.moduleDescription(workflowId = workflowId, branchId = branchId), position)
}
case class DeleteCell(position: Int) extends WorkflowDelta
{
  def serialize(workflowId: Identifier, branchId: Identifier)(implicit session: DBSession) = 
    SerializedDeleteCell(position)
}

sealed trait SerializedWorkflowDelta

case class SerializedInsertCell(cell: ModuleDescription, position: Int) extends SerializedWorkflowDelta
object SerializedInsertCell { implicit val format: Format[SerializedInsertCell] = Json.format }

case class SerializedUpdateCell(cell: ModuleDescription, position: Int) extends SerializedWorkflowDelta
object SerializedUpdateCell { implicit val format: Format[SerializedUpdateCell] = Json.format }

case class SerializedDeleteCell(position: Int) extends SerializedWorkflowDelta
object SerializedDeleteCell { implicit val format: Format[SerializedDeleteCell] = Json.format }

object SerializedWorkflowDelta
{
  val OP_TYPE = "operation"

  val INSERT_CELL = "insert_cell"
  val UPDATE_CELL = "update_cell"
  val DELETE_CELL = "delete_cell"

  implicit val format: Format[SerializedWorkflowDelta] = Format(
    new Reads[SerializedWorkflowDelta]() {
      def reads(j: JsValue): JsResult[SerializedWorkflowDelta] =
        (j \ OP_TYPE).as[String] match {
          case INSERT_CELL => JsSuccess(j.as[SerializedInsertCell])
          case UPDATE_CELL => JsSuccess(j.as[SerializedUpdateCell])
          case DELETE_CELL => JsSuccess(j.as[SerializedDeleteCell])
          case _ => JsError()
        }
    },
    new Writes[SerializedWorkflowDelta]() {
      def writes(d: SerializedWorkflowDelta): JsValue =
        d match { 
          case x:SerializedInsertCell => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(INSERT_CELL))
          case x:SerializedUpdateCell => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(UPDATE_CELL))
          case x:SerializedDeleteCell => Json.toJson(x).as[JsObject] + (OP_TYPE -> JsString(DELETE_CELL))
        }
    }
  )
}