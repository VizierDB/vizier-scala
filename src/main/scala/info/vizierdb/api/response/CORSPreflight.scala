/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.Response

case class CORSPreflightResponse(methodStrings: String*)
  extends Response
{
  val methods = methodStrings.mkString(",")

  def write(output: HttpServletResponse) =
  {
    output.setHeader("Access-Control-Allow-Headers", Seq(
      "content-type"
    ).mkString(", "))
    output.setHeader("Allow", methods)
    output.setHeader("Access-Control-Allow-Methods", methods)
  }
}

