package info.vizierdb.api.handler

import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.api.response.VizierErrorResponse
import info.vizierdb.util.StringUtils.ellipsize
import com.typesafe.scalalogging.LazyLogging
import org.mimirdb.util.JsonUtils.stringifyJsonParseErrors

class JsonHandler[R <: Request](
  implicit val format: Format[R]
)
  extends Handler
  with LazyLogging
{

  def handle(
    pathParameters: Map[String, JsValue], 
    request: HttpServletRequest 
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
}

object JsonHandler
{
  def apply[R <: Request](implicit format: Format[R]): JsonHandler[R] = 
    new JsonHandler[R]
}