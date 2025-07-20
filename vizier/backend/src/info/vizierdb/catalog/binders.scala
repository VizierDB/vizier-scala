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
package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import java.sql.ResultSet
import info.vizierdb.types._
import info.vizierdb.serialized
import info.vizierdb.serializers._

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
  
  //// ArtifactType ////
  implicit val artifactTypeTypeBinder: TypeBinder[ArtifactType.T] = new TypeBinder[ArtifactType.T] {
    def apply(rs: ResultSet, label: String): ArtifactType.T = ArtifactType(rs.getInt(label))
    def apply(rs: ResultSet, index: Int): ArtifactType.T = ArtifactType(rs.getInt(index))
  }
  implicit val artifactTypeParameterBinder = ParameterBinderFactory[ArtifactType.T] {
    value => (stmt, idx) => stmt.setInt(idx, value.id)
  }
  
  //// StreamType ////
  implicit val streamTypeTypeBinder: TypeBinder[StreamType.T] = new TypeBinder[StreamType.T] {
    def apply(rs: ResultSet, label: String): StreamType.T = StreamType(rs.getInt(label))
    def apply(rs: ResultSet, index: Int): StreamType.T = StreamType(rs.getInt(index))
  }
  implicit val streamTypeParameterBinder = ParameterBinderFactory[StreamType.T] {
    value => (stmt, idx) => stmt.setInt(idx, value.id)
  }

  //// PythonPackageSet ////
  implicit val pythonPackageSetTypeBinder: TypeBinder[Seq[serialized.PythonPackage]] = new TypeBinder[Seq[serialized.PythonPackage]] {
    def apply(rs: ResultSet, label: String): Seq[serialized.PythonPackage] = Json.parse(rs.getString(label)).as[Seq[serialized.PythonPackage]]
    def apply(rs: ResultSet, index: Int): Seq[serialized.PythonPackage] = Json.parse(rs.getString(index)).as[Seq[serialized.PythonPackage]]
  }
  implicit val pythonPackageSetParameterBinder = ParameterBinderFactory[Seq[serialized.PythonPackage]] {
    value => (stmt, idx) => stmt.setString(idx, Json.toJson(value).toString)
  }
}

