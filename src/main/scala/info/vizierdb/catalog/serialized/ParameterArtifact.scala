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
package info.vizierdb.catalog.serialized

import play.api.libs.json._
import org.apache.spark.sql.types.DataType
import org.mimirdb.spark.Schema.dataTypeFormat
import org.mimirdb.spark.{ Schema => SparkSchema }
import org.mimirdb.spark.SparkPrimitive

case class ParameterArtifact(
  value: Any,
  dataType: DataType
)
{
  def jsonValue = SparkPrimitive.encode( value, dataType )
  def jsonDataType = SparkSchema.encodeType(dataType)
}

object ParameterArtifact
{
  implicit val format: Format[ParameterArtifact] = Format[ParameterArtifact](
    new Reads[ParameterArtifact]{
      def reads(j: JsValue): JsResult[ParameterArtifact] =
      {
        val dataType = SparkSchema.decodeType( (j \ "dataType").as[String] )
        val value = SparkPrimitive.decode( (j \ "value").get, dataType)
        JsSuccess(ParameterArtifact(value, dataType))
      }
    },
    new Writes[ParameterArtifact]{
      def writes(p: ParameterArtifact): JsValue =
        Json.obj(
          "value" -> p.jsonValue,
          "dataType" -> p.jsonDataType
        )
    }
  )
}