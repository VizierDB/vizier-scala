package info.vizierdb.api.handler

import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.{ Request, Response }

abstract class SimpleHandler extends Handler
{
  def handle(pathParameters: Map[String, JsValue]): Response

  def handle(
    pathParameters: Map[String, JsValue], 
    connection: ClientConnection 
  ): Response = handle(pathParameters)
}