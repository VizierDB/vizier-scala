package info.vizierdb.util

import scala.play.json._
import java.net.URL
import info.vizierdb.api.handler._

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
      "paths" -> renderRoutes(routes, "/", Map.empty)
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
          (s"$path/{$tailName}", pathFields ++ Map(name -> FieldType.TAIL))
        }.unzip
        Map(
          tailPath.getOrElse { path } -> 
            route.handlers.toSeq.map { case (method, handler) => 
              JsonObject(Map(
                method.toString.toLowerCase -> 
                  renderHandler(handler, tailField.getOrElse(pathFields))
              ))
            }
        )
      }
    val childRoutes:Map[String, JsObject] = 
      route.children.flatMap { case (pathElement, childRoute) => 
        renderRoute(childRoute, s"$path/$pathElement", pathFields)
      } ++ route.fieldParser.map { case (name, t, _, childRoute) => 
        renderRoute(childRoute, s"$path/{$name}", pathFields ++ Map(name -> t))
      } 

    return handlerRoutes ++ childRoutes
  }

  def renderHandler(
    handler: Handler, 
    pathFields: Map[String, FieldType.T]
  ) = 
  {
    ???
  }

  def main(args: Seq[String]) =
  {
    generate(
      title = "Vizier",
      version = "0.2",
      description = "A reusability-focused polyglot notebook",
      contact = ("Vizier Issues", new URL("https://github.com/VizierDB/web-ui/issues")),
      license = ("Apache 2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.html"))
    )
  }
}