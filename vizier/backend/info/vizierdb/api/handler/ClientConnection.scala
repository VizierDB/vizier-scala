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
package info.vizierdb.api.handler

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import java.io.InputStream
import play.api.libs.json.{ JsValue, Json }

abstract class ClientConnection {
  def getInputStream: InputStream
  def getPart(part: String): (InputStream, String)
  def getParameter(name: String): String
  def getJson: JsValue = 
    Json.parse(
      scala.io.Source.fromInputStream(getInputStream).mkString 
    )
}

class JettyClientConnection(request: HttpServletRequest, response: HttpServletResponse)
  extends ClientConnection
{
  def getInputStream: InputStream = request.getInputStream()

  def getPart(part: String): (InputStream, String) = 
  {
    val segment = request.getPart(part)
    if(segment == null){
      throw new IllegalArgumentException(s"Parameter '$part' not provided")
    }
    return (
      segment.getInputStream(),
      segment.getSubmittedFileName()
    )
  }
  def getParameter(name: String): String = 
    request.getParameter(name)
}

