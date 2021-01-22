package info.vizierdb.api.handler

import play.api.libs.json._
import scala.reflect._
import scala.reflect.runtime.universe._
import json.{ Schema => JsonSchema }

sealed trait Content

case class JsonContent[T](
  schema: JsValue,
  description: String = "",
)(
  examples: String*
) extends Content

case class HandlerResponse(
  content: Seq[Content] = Seq.empty,
  code: Int = 200,
  description: String = "",
) extends scala.annotation.StaticAnnotation
