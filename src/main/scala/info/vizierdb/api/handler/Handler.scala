package info.vizierdb.api.handler

import scala.collection.mutable.Buffer
import scala.annotation.ClassfileAnnotation
import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.util.StringUtils

abstract class Handler
{

  def summary: String = 
    StringUtils.camelCaseToHuman(
      this.getClass
          .getSimpleName
          .replace("$", "")
          .replace("Handler", "")
    )
  def details = ""
  def responses: Buffer[HandlerResponse] = Buffer.empty
  def requestBody: Option[Content] = None


  def handle(
    pathParameters: Map[String, JsValue], 
    request: HttpServletRequest 
  ): Response

}

