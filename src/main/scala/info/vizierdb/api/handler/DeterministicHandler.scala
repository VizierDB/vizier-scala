package info.vizierdb.api.handler

import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.{ Request, Response }

abstract class DeterministicHandler extends Handler
{
  def handle: Response

  def handle(
    pathParameters: Map[String, JsValue], 
    request: HttpServletRequest 
  ): Response = handle
}