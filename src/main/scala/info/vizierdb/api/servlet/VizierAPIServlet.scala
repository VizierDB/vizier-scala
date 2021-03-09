package info.vizierdb.api.servlet

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.api.Response
import info.vizierdb.api.response.VizierErrorResponse
import info.vizierdb.VizierAPI
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
        case e: Throwable => 
          logger.error(e.getMessage + "\n" + e.getStackTrace.map { _.toString }.mkString("\n"))
          VizierErrorResponse(
            e.getClass.getCanonicalName(),
            e.getMessage()
          )
      }
    logger.trace(s"$response")
    if(VizierAPI.debug){
      output.setHeader("Access-Control-Allow-Origin", "*")
    }
    response.write(output)
  }
}