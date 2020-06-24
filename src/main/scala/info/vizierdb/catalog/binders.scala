package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import java.sql.ResultSet
import info.vizierdb.types._

object binders
{
  //// Raw JSON ////
  implicit val jsonTypeBinder: TypeBinder[JsValue] = new TypeBinder[JsValue] {
    def apply(rs: ResultSet, label: String): JsValue = Json.parse(rs.getString(label))
    def apply(rs: ResultSet, index: Int): JsValue = Json.parse(rs.getString(index))
  }
  implicit val jsonParameterBinder = ParameterBinderFactory[JsValue] {
    value => (stmt, idx) => stmt.setString(idx, value.toString)
  }

  //// JSON Objects ////
  implicit val jsonObjectTypeBinder: TypeBinder[JsObject] = new TypeBinder[JsObject] {
    def apply(rs: ResultSet, label: String): JsObject = Json.parse(rs.getString(label)).as[JsObject]
    def apply(rs: ResultSet, index: Int): JsObject = Json.parse(rs.getString(index)).as[JsObject]
  }
  implicit val jsonObjectParameterBinder = ParameterBinderFactory[JsObject] {
    value => (stmt, idx) => stmt.setString(idx, value.toString)
  }

  //// ActionType ////
  implicit val actionTypeTypeBinder: TypeBinder[ActionType.T] = new TypeBinder[ActionType.T] {
    def apply(rs: ResultSet, label: String): ActionType.T = ActionType(rs.getInt(label))
    def apply(rs: ResultSet, index: Int): ActionType.T = ActionType(rs.getInt(index))
  }
  implicit val actionTypeParameterBinder = ParameterBinderFactory[ActionType.T] {
    value => (stmt, idx) => stmt.setInt(idx, value.id)
  }
  
  //// ExecutionState ////
  implicit val executionStateTypeBinder: TypeBinder[ExecutionState.T] = new TypeBinder[ExecutionState.T] {
    def apply(rs: ResultSet, label: String): ExecutionState.T = ExecutionState(rs.getInt(label))
    def apply(rs: ResultSet, index: Int): ExecutionState.T = ExecutionState(rs.getInt(index))
  }
  implicit val executionStateParameterBinder = ParameterBinderFactory[ExecutionState.T] {
    value => (stmt, idx) => stmt.setInt(idx, value.id)
  }
}