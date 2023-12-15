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
package info.vizierdb.api.response

import play.api.libs.json._
import info.vizierdb.api.JsonResponse
import javax.servlet.http.HttpServletResponse

case class RawJsonResponse(
  val data: JsValue, 
  override val status: Int = HttpServletResponse.SC_OK
) extends JsonResponse[RawJsonResponse]

object RawJsonResponse
{
  implicit val format:Format[RawJsonResponse] = Format[RawJsonResponse](
    new Reads[RawJsonResponse] { def reads(j: JsValue) = JsSuccess(RawJsonResponse(j)) },
    new Writes[RawJsonResponse] { def writes(v: RawJsonResponse) = v.data }
  )
}

