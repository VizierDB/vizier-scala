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
package info.vizierdb.api.websocket

import play.api.libs.json.JsValue
import info.vizierdb.api.handler.ClientConnection
import java.io.{ InputStream, StringBufferInputStream }
import play.api.libs.json.JsObject

class WebsocketRequestConnection(request: JsObject)
  extends ClientConnection
{
  lazy val getInputStream: InputStream = 
    new StringBufferInputStream(request.toString())

  override def getJson: JsValue = request
  def getParameter(name: String): String = 
    (request \ name).asOpt[String].getOrElse { null }
  def getPart(name: String) =
    throw new UnsupportedOperationException(
      "Websocket requests don't support file transfer"
    )
}