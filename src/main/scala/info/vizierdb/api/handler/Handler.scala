package info.vizierdb.api.handler

import scala.annotation.ConstantAnnotation
import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.{ Request, Response }

trait HandlerMetadata
{
  def summary: String
  def description: String  
}

trait Handler extends HandlerMetadata
{
  def handle(
    pathParameters: Map[String, JsValue], 
    request: HttpServletRequest 
  ): Response

}