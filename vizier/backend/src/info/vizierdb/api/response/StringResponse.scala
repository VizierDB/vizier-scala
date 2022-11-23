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
import info.vizierdb.api.BytesResponse
import javax.servlet.http.HttpServletResponse
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes

case class StringResponse(
  data: String, 
  override val contentType: ContentType = ContentTypes.`text/plain(UTF-8)` ,
  override val status: Int = HttpServletResponse.SC_OK
) extends BytesResponse
{
  def getBytes = data.getBytes()
}

