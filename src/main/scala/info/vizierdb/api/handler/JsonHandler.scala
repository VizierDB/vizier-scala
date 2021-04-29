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

import play.api.libs.json._
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.api.response.VizierErrorResponse
import info.vizierdb.util.StringUtils.ellipsize
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.util.JsonUtils.stringifyJsonParseErrors
import info.vizierdb.util.StringUtils
import scala.reflect.runtime.universe._
import json.Json.{ schema => jsonSchemaOf }
import json.{ Schema => JsonSchema }

class JsonHandler[R <: Request]()(
  implicit val format: Format[R],
  implicit val requestTag: TypeTag[R]
)
  extends Handler
  with LazyLogging
{
  val requestType = typeOf[R]

  def handle(
    pathParameters: Map[String, JsValue], 
    request: ClientConnection 
  ): Response = {
    val text = scala.io.Source.fromInputStream(request.getInputStream).mkString 
    // logger.debug(s"$text")
    val parsed: Either[Request, Response] = 
      try { 
        var parsed = Json.parse(text)

        if(!pathParameters.isEmpty){
          parsed = JsObject(
            parsed.as[Map[String,JsValue]]
              ++ pathParameters
          )
        }
        Left(parsed.as[R])
      } catch {
        case e@JsResultException(errors) => {
          logger.error(e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("\n"))
          Right(VizierErrorResponse(
            e.getClass().getCanonicalName(),
            s"Error(s) parsing API request\n${ellipsize(text, 100)}\n"+stringifyJsonParseErrors(errors).mkString("\n")
          ))
        }
        case e:Throwable => {
          logger.error(e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("\n"))
          Right(VizierErrorResponse(
            e.getClass().getCanonicalName(),
            s"Error(s) parsing API request\n${ellipsize(text, 100)}\n"
          ))
        }
      }

    parsed match {
      case Left(request) => request.handle
      case Right(response) => response
    }
  }

  override def summary =
    StringUtils.camelCaseToHuman(
      requestType.toString
                 .split("\\.")
                 .last
                 .replace("$", "")
    )

  override def requestBody = Some(JsonContent[R](Json.obj())())
}

object JsonHandler
{
  import scala.language.experimental.macros
  import scala.reflect.macros.blackbox

  def apply[R <: Request](
    implicit 
      tag: TypeTag[R],
      format: Format[R]
  ) =
    new JsonHandler[R]()
}

