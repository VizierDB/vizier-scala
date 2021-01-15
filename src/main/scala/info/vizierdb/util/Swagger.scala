package info.vizierdb.util

import play.api.libs.json._
import java.net.URL
import info.vizierdb.api.handler._
import info.vizierdb.VizierServlet
import algebra.ring.Field

object Swagger 
{

  def generate(
    title: String,
    version: String,
    description: String,
    contact: (String, URL),
    license: (String, URL),
    routes: Route,
  ): JsObject = {
    Json.obj(
      "title" -> title,
      "version" -> version,
      "description" -> description,
      "contact" -> Json.obj("name" -> contact._1, "url" -> contact._2.toString),
      "license" -> Json.obj("name" -> license._1, "url" -> license._2.toString),
      "paths" -> renderRoutes(routes, "", Map.empty)
    )
  }

  def renderRoutes(
    route: Route, 
    path: String, 
    pathFields: Map[String,FieldType.T]
  ): Map[String, JsObject] = 
  {

    val handlerRoutes: Map[String, JsObject] = 
      if(route.handlers.isEmpty) { Map.empty }
      else {
        val (tailPath, tailField) = route.tailField.map { tailName => 
          (s"$path/{$tailName}", pathFields ++ Map(tailName -> FieldType.TAIL))
        }.unzip
        Map(
          tailPath.headOption.getOrElse { path } -> 
            JsObject(
              route.handlers.map { case (method: RequestMethod.T, handler: Handler) => 
                  method.toString.toLowerCase -> 
                    renderHandler(handler, tailField.headOption.getOrElse(pathFields))
              }.toMap
            )
        )
      }
    val childRoutes:Map[String, JsObject] = (
      route.children.flatMap { case (pathElement, childRoute) => 
        renderRoutes(childRoute, s"$path/$pathElement", pathFields).toSeq
      } ++ route.fieldParser.toSeq.flatMap { case (name, t, _, childRoute) => 
        renderRoutes(childRoute, s"$path/{$name}", pathFields ++ Map(name -> t)).toSeq
      } 
    ).toMap

    return handlerRoutes ++ childRoutes
  }

  def renderHandler(
    handler: Handler, 
    pathFields: Map[String, FieldType.T]
  ): JsObject = 
  {
    var elements = Map[String,JsValue](
      "summary" -> JsString(handler.summary),
      "details" -> JsString(handler.details)
    )
    if(!pathFields.isEmpty) {
      elements = elements ++ Map[String,JsValue](
        "parameters" -> JsArray(
          pathFields.toSeq.map { case (name: String, t:FieldType.T) => 
            Json.obj(
              "name" -> name,
              "in" -> "path",
              "required" -> true,
              "type" -> JsString(t match { 
                case FieldType.TAIL => "string"
                case FieldType.STRING => "string"
                case FieldType.INT => "long"
              }),
            )
          }
        )
      )
    }
    return JsObject(elements)
  }

  def main(args: Array[String]): Unit =
  {
    println(Json.prettyPrint(generate(
      title = "Vizier",
      version = "0.2",
      description = "A reusability-focused polyglot notebook",
      contact = ("Vizier Issues", new URL("https://github.com/VizierDB/web-ui/issues")),
      license = ("Apache 2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.html")),
      routes = VizierServlet.routes
    )))
  }
}