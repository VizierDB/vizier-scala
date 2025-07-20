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
package info.vizierdb.spark.caveats

import play.api.libs.json._
import org.mimirdb.caveats.{ Caveat }
import org.apache.spark.sql.catalyst.expressions.Literal
import org.apache.spark.sql.types.DataType
import info.vizierdb.spark.SparkPrimitive


object CaveatFormat
{
  private implicit val literalFormat: Format[Literal] = Format(
    new Reads[Literal] { 
      def reads(literal: JsValue): JsResult[Literal] = {
        val fields = literal.as[Map[String,JsValue]]
        // Note, we *want* the quotation marks and escapes on the following 
        // line, since spark annoyingly hides the non-json version from us.
        val t = DataType.fromJson(fields("dataType").toString) 
        JsSuccess(
          Literal(SparkPrimitive.decode(fields("value"), t))
        )
      }
    },
    new Writes[Literal] {
      def writes(literal: Literal): JsValue = {
        Json.obj( 
          "dataType" -> literal.dataType.typeName,
          "value" -> SparkPrimitive.encode(literal.value, literal.dataType)
        )
      }
    }
  )

  implicit val caveatFormat: Format[Caveat] = Json.format
}