package info.vizierdb.api.handler

import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.{ Request, Response }
import scala.util.matching.Regex
import java.util.regex.MatchResult
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.api.response.VizierErrorResponse

case class Route(
  handlers: Map[RequestMethod.T, Handler],
  children: Map[String,Route],
  fieldParser: Option[(String, FieldType.T, String => JsValue, Route)],
  tailField: Option[String]
)
  extends Response
  with LazyLogging
{
  val methodStrings: String = handlers.keys.map { _.toString }.mkString(", ")

  def write(output: HttpServletResponse) =
  {
    output.setHeader("Access-Control-Allow-Headers", Seq(
      "content-type"
    ).mkString(", "))
    output.setHeader("Allow", methodStrings)
    output.setHeader("Access-Control-Allow-Methods", methodStrings)
  }


  def handle(
    pathComponents: Seq[String],
    idx: Int,
    requestMethod: RequestMethod.T,
    pathParameters: Map[String, JsValue], 
    request: HttpServletRequest 
  ): Response =
  {
    def FOUR_OH_FOUR = {
      // if we've gotten here, this is a 404
      logger.error(s"${requestMethod} Not Handled: '${request.getPathInfo}' (failed at '${pathComponents.slice(0, idx).mkString("/")}')")
      VizierErrorResponse(
        "NotFound",
        s"${request.getMethod} Not Handled: ${request.getPathInfo}",
        HttpServletResponse.SC_NOT_FOUND
      )

    }

    logger.trace(s"Path depth $idx in ${pathComponents.mkString("/")}")

    if(idx >= pathComponents.size || tailField.isDefined){
      logger.trace(s"Response with available handlers: $methodStrings\n${pathParameters.toSeq.map { x => "  "+x._1+":"+x._2.toString}.mkString("\n")}")
      if(requestMethod.equals(RequestMethod.OPTIONS)){
        return this
      } else {
        handlers.get(requestMethod) match {
          case None => FOUR_OH_FOUR
          case Some(handler) => 
            return handler.handle(
              pathParameters ++ 
                tailField.map { 
                  _ -> Json.toJson(pathComponents.drop(idx+1))
                }.toMap,
              request
            )
        }
      }
    } else {
      logger.trace(s"Available children: ${children.keys.mkString(", ")}")
      children.get(pathComponents(idx))
              .map { _.handle(
                          pathComponents, 
                          idx + 1, 
                          requestMethod,
                          pathParameters,
                          request
                        )}
              .orElse { fieldParser.map { 
                case (name, _, parser, route) =>
                  logger.trace(s"Parsing field: ${pathComponents(idx)}")
                  route.handle(
                    pathComponents,
                    idx+1,
                    requestMethod,
                    Map(name -> parser(pathComponents(idx))) ++: pathParameters,
                    request
                  )
              }}
              .getOrElse { FOUR_OH_FOUR }
    }
  }

}

object Route
{
  import scala.reflect.runtime.universe._
  
  type Path = Either[(RequestMethod.T, Handler), (String, Route)]

  object implicits {
    implicit def embedHandler[T <: Handler](h: (RequestMethod.T, T)): Path = Left(h)
    implicit def embedRoute(r: (String, Route)): Path = Right(r)
  }

  def apply(paths: Path*): Route =
  {
    val handlers = paths.collect { case Left(l) => l }
    val children = paths.collect { case Right(r) => r }

    val (parameterChildren, directChildren) = 
      children.partition { _._1.startsWith(":") }

    assert(parameterChildren.size <= 1, "Only one parameter per routing node")

    val (parameterRoute, tailParameter) = 
      parameterChildren.headOption.map { case (param, route) =>
        val paramElements = param.split(":")
        assert(paramElements.size == 3, "Parameter routes must have format ':type:name'")
        val fieldType = FieldType.withName(paramElements(1).toUpperCase())
        val fieldName = paramElements(2)
        if(fieldType == FieldType.TAIL){
          (None, Some(fieldName))
        } else {
          val fieldParser = FieldType.parserFor(fieldType)
          (Some(fieldName, fieldType, fieldParser, route), None)
        }
      }.getOrElse { (None, None) }

    Route(
      handlers.toMap,
      directChildren.toMap,
      parameterRoute,
      tailParameter
    )
  }

}

