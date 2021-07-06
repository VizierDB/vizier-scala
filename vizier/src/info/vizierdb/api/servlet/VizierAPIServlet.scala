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
package info.vizierdb.api.servlet

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.api.Response
import info.vizierdb.api.response.{ VizierErrorResponse, ErrorResponse }
import info.vizierdb.{ Vizier, VizierAPI }
import play.api.libs.json.JsResultException
import info.vizierdb.util.JsonUtils

object VizierAPIServlet
  extends HttpServlet 
  with LazyLogging
  with VizierAPIServletRoutes
{
  def fourOhFour(request: HttpServletRequest): Response = 
  {
    logger.error(s"${request.getMethod()} Not Handled: '${request.getPathInfo}'")
    VizierErrorResponse(
      "NotFound",
      s"${request.getMethod} Not Handled: ${request.getPathInfo}",
      HttpServletResponse.SC_NOT_FOUND
    )
  }

  def processResponse(request: HttpServletRequest, output: HttpServletResponse)
                     (baseResponse: => Response): Unit =
  {
    val response: Response = 
      try {
        logger.debug(s"API ${request.getMethod} ${request.getPathInfo}})")
        baseResponse
      } catch {
        case e: JsResultException => 
          logger.error(e.getMessage + "\n" + e.getStackTrace.map { _.toString }.mkString("\n"))
          VizierErrorResponse(
            e.getClass.getCanonicalName(),
            "Json Errors: "+JsonUtils.prettyJsonParseError(e).mkString(", ")
          )
        case ErrorResponse(e: Response) => 
          logger.error(e.toString)
          e
        case e: Throwable => 
          logger.error(e.getMessage + "\n" + e.getStackTrace.map { _.toString }.mkString("\n"))
          VizierErrorResponse(
            e.getClass.getCanonicalName(),
            e.getMessage()
          )
      }
    logger.trace(s"$response")
    if(Vizier.config.devel() || Vizier.config.connectFromAnyHost()){
      output.setHeader("Access-Control-Allow-Origin", "*")
    }
    response.write(output)
  }
}

