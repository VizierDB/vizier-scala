package info.vizierdb.api.handler

import scala.collection.mutable.Buffer
import scala.annotation.ClassfileAnnotation
import play.api.libs.json._
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
  def filePart: Option[String] = None

  def handle(
    pathParameters: Map[String, JsValue], 
    request: ClientConnection 
  ): Response

}

