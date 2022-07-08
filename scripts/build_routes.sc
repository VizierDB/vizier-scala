import scala.collection.mutable

// Utilities for processing vizier-routes.txt into source code

val SOURCE_DIR = os.pwd / "vizier"
val BACKEND_DIR = SOURCE_DIR / "backend" / "src" / "info" / "vizierdb"
val UI_DIR      = SOURCE_DIR / "ui"      / "src" / "info" / "vizierdb"

// vizier-routes.txt contains a list of routes with information about the
// structure of the request, handler information, and other metadata.
// The file is whitespace-delimited, with the following columns:
//  1. route: '/'-delimited.  An entry of the form {label:type} indicates a 
//     variable parameter that will be assigned the specified name.  See Path
//     Types below
//  2. HTTP Verb: e.g. GET, POST, etc...
//  3. Domain: A label indicating the subject of the action.  Not used directly
//     but can serve to differentiate different authentication domains (See
//     Domains below)
//  4. Action: A short tag identifying the action.  These are used to create
//     method names in generated classes, and to make everything readable.
//  5. Handler: The name of an object in the `info.vizierdb.api` package that
//     has an `apply` method that accepts all of the listed parameters.
//  6. Return Payload: The scala return-type of the handler, or the special
//     return type FILE[mime/type].  This class must be play-json-serializable.
//     See FILEs below
//  7. A ';'-delimited list of "label:type" pairs indicating json-serialized 
//     parameters to the method.  These will be deserialized and passed as 
//     arguments to the function.  Types are Scala types, or the special
//     type FILE (see below)
// 
// --- Path Types ---
// - long: Translates to Long or Identifier
// - int: Translates to Int
// - subpath: Must be the last element of a route.  Matches everything and 
//            returnsa string containing the rest of the route
// 
// --- Domains ---
// - service: General API methods not related to any specific project
// - project: One project (group of notebooks)
// - branch: One branch in a project
// - workflow: One workflow in one branch in a project
// - artifact: An artifact in a project
// - published: An artifact that has been published
// - fs: API access to the filesystem (or other data source connectors)
//
// --- FILEs ---
// Some routes need direct access to file IO.  For example when downloading 
// a file, the handler needs to be able to return a file.
//

val INPUT = SOURCE_DIR / "resources" / "vizier-routes.txt"

/////////////////////// Backend /////////////////////// 
// Vizier uses Akka to route http requests to handlers.
// This script will generate It will generate a single file 
val SERVER_ROUTES = BACKEND_DIR / "api" / "akka"
def DOMAIN_ROUTES(domain: String) = SERVER_ROUTES / s"RoutesFor${domain.capitalize}.scala"
val MAIN_ROUTES   = SERVER_ROUTES / "AllRoutes.scala"

// We also provide an API for invoking many of the routes 
// over the websocket (this is necessary to ensure ordering
// and avoid race conditions).  The backend component of this API
// is here:
val WEBSOCKET_IMPL = BACKEND_DIR / "api" / "websocket" / "BranchWatcherAPIRoutes.scala"

///////////////////////   UI    ///////////////////////

// We ensure typesafe invocation of API methods in the frontend by
// providing a proxy wrapper.
val API_PROXY = UI_DIR / "ui" / "network" / "API.scala"

// And a similar wrapper for invoking methods over the websocket.
val WEBSOCKET_PROXY = UI_DIR / "ui" / "network" / "BranchWatcherAPIProxy.scala"

val AUTOGEN_HEADER = 
  """/***************************************************************/
    |/**              DO NOT EDIT THIS FILE DIRECTLY               **/
    |/** It was automatically generated by scripts/build_routes.sc **/
    |/** Edit that file instead.                                   **/
    |/***************************************************************/
    """.stripMargin


///////////////////////////////////////////////////////
////////////// Read vizier-routes.txt /////////////////
///////////////////////////////////////////////////////

sealed trait PathComponent

case class PathLiteral(value: String) extends PathComponent
case class PathVariable(identifier: String, dataType: String) extends PathComponent
{
  def scalaType = 
    dataType match {
      case "int" => "Int"
      case "long" => "Long"
      case "subpath" => "String"
      case "string" => "String"
    }

  def toParam = 
    Param(identifier, scalaType)

  def cast(target: String) = 
    dataType match {
      case "int"  => s"$target.toInt"
      case "long" => s"$target.toLong"
      case "subpath" => target
      case "string" => target
    }
}

case class Param(identifier: String, scalaType: String)
{
  def typedIdentifier = s"$identifier:$scalaType"

  def nativeScalaType = 
    scalaType.replaceAll("UndefOr", "Option")

  def typedNativeIdentifier = s"$identifier:$nativeScalaType"

  def isOptional =
    scalaType.startsWith("UndefOr") || scalaType.startsWith("Option")
}
case class Route(
  path : Seq[PathComponent],
  pathQueryParams: Seq[PathVariable],
  verb : String,
  domain: String,
  action: String,
  handler: String,
  returns: String,
  jsonParams: Seq[Param],
  fileParam: Option[String],
)
{
  def allParams = Seq[Seq[Param]](
    pathParams,
    pathQueryParams.map { _.toParam },
    jsonParams
  ).flatten

  def pathParams = 
    path.collect { case p:PathVariable => (p:PathVariable).toParam }

  def jsonParamClassName: Option[String] =
    if(jsonParams.isEmpty){ None }
    else { 
      Some(s"${action.capitalize}Parameter")
    }

  def jsonParamClass: Option[String] =
    jsonParamClassName.map { clazz => 
      s"""case class $clazz(
         |  ${jsonParams.map { _.typedNativeIdentifier }.mkString(",\n  ")}
         |)""".stripMargin
    }
  
  def actionLabel = s"${domain}${action.split("_").map { _.capitalize }.mkString}"
}

val PATH_VARIABLE = "\\{(\\w+):(\\w+)\\}".r

val ROUTES: Seq[Route] = 
  os.read(INPUT).split("\n")
    .map { description => 
      val components = description.split("\\s+")
      val pathAndArguments = components(0).split("\\?")
      val path = pathAndArguments(0).split("/").toSeq
      val pathQuery:Seq[PathVariable] = 
        if(pathAndArguments.size > 1){ 
          pathAndArguments(1).split("&")
                             .map { _.split(":").toSeq }
                             .map {
                              case Seq(identifier, dataType) => PathVariable(identifier, dataType)
                             }
        } else { Seq.empty }
      assert(path.isEmpty || path(0) == "")

      val jsonParams:Seq[Param] =
        components(6) match {
          case "-" | "_" => Seq()
          case x => x.split(";")
            .map { _.split(":").toSeq }
            .map { 
              case Seq(identifier, dataType) => Param(identifier, dataType) 
              case x => throw new Exception(s"Invalid parameter: ${x.mkString(",")}")
            }
        }

      val paramIsFile =
        !jsonParams.filter { _.scalaType == "FILE" }.isEmpty

      assert(!paramIsFile || jsonParams.size == 1)

      Route(
        path = path.drop(1).map {  
          case PATH_VARIABLE(identifier, dataType) => PathVariable(identifier, dataType)
          case x => PathLiteral(x)
        },
        pathQueryParams = pathQuery,
        verb = components(1),
        domain = components(2),
        action = components(3),
        handler = components(4),
        returns = components(5),
        jsonParams = if(paramIsFile){ Seq() } else { jsonParams },
        fileParam = if(paramIsFile){ Some(jsonParams(0).identifier) } else { None }
      )
    }

///////////////////////////////////////////////////////
//////////////////  Akka Routes   /////////////////////
///////////////////////////////////////////////////////

def akkaRouteHandler(routeAndIndex: (Route, Int)): String =
{
  val (route: Route, idx: Int) = routeAndIndex
  var path: Seq[String] = 
    route.path.map {
      case PathLiteral(component) => s"\"$component\""
      case PathVariable(_, "int") => "IntNumber"
      case PathVariable(_, "long") => "LongNumber"
      case PathVariable(_, "subpath") => "Remaining"
      case PathVariable(_, "string") => "Segment"
    }

  val (pathParamInputs, pathParamOutputs) = 
    route.path.collect { 
      case p:PathVariable => 
        (p.toParam.typedIdentifier, 
          s"${p.identifier} = ${p.identifier}")
    }.unzip

  var extractors = mutable.ArrayBuffer[String]()

  if(!pathParamInputs.isEmpty) {
    extractors.append(
      s"(${pathParamInputs.mkString(", ")}) => {"
    )
  }

  val jsonParamOutputs =
    route.jsonParams.map { param =>
      s"${param.identifier} = jsonEntity.${param.identifier}"
    }

  if(!jsonParamOutputs.isEmpty){
    extractors.append("decodeRequest {")
    extractors.append(
      s"entity(as[${route.jsonParamClassName.get}]) { jsonEntity => "
    )
  }

  val fileParamOutputs = 
    route.fileParam.map { identifier => 
      extractors.append(s"VizierServer.withFile(\"${identifier}\") { fileEntity => ")
      s"$identifier = fileEntity"
    }

  val queryStringParamOutputs =
    route.pathQueryParams.map { param =>
      s"${param.identifier} = query.get(\"${param.identifier}\").map { x => ${param.cast("x")} }"
    }

  if(!queryStringParamOutputs.isEmpty){
    extractors.append("extractRequest { httpRequest => val query = httpRequest.getUri.query.toMap.asScala")
  }

  val allParams =
    pathParamOutputs ++
    jsonParamOutputs ++
    fileParamOutputs ++
    queryStringParamOutputs

  s"""  val ${route.action}Route${idx} =
     |    ${if(path.isEmpty){""} else { s"path(${path.mkString(" / ")}) {"}} ${extractors.map { "\n      "+_ }.mkString}
     |        ${route.verb.toLowerCase} { 
     |          ${route.handler}(${allParams.mkString(", ")})
     |        }${if(extractors.isEmpty){""} else { "\n      "+extractors.map { _ => "}" }.mkString(" ") }}
     |    ${if(path.isEmpty){""} else { "}" }}
     |  """.stripMargin
}

val routesByDomain = ROUTES.groupBy { _.domain }

for( (domain, routes) <- routesByDomain )
{
  val file = DOMAIN_ROUTES(domain)
  val clazz = file.baseName

  val jsonParamClasses =
    routes.flatMap { _.jsonParamClass.map { _.split("\n")
                                             .map { "  " + _ }
                                             .mkString("\n") } }
          .mkString("\n\n")
  val data = 
    s"""package info.vizierdb.api.akka
       |$AUTOGEN_HEADER
       |import play.api.libs.json._
       |import akka.http.scaladsl.model._
       |import akka.http.scaladsl.server.Directives._
       |import akka.http.scaladsl.model.headers.`Content-Type`
       |import akka.http.scaladsl.model.HttpHeader
       |import info.vizierdb.api._
       |import info.vizierdb.serialized
       |import info.vizierdb.serializers._
       |import info.vizierdb.types._
       |import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
       |import VizierServer.RouteImplicits._
       |import scala.jdk.CollectionConverters._
       |import info.vizierdb.spark.caveats.CaveatFormat._
       |
       |object $clazz
       |{
       |$jsonParamClasses
       |
       |${routes.flatMap { _.jsonParamClassName }
                .map { n => s"  implicit val ${n}Format: Format[$n] = Json.format"}
                .mkString("\n")}
       |
       |${routes.zipWithIndex.map { akkaRouteHandler(_)}.mkString("\n\n")}
       |
       |  val routes = concat(
       |    ${routes.zipWithIndex.map { case (route, idx) => s"${route.action}Route$idx" }.mkString(",\n    ")}
       |  )
       |}
       """.stripMargin

  os.write.over(file, data)
}

{
  val file = MAIN_ROUTES
  val clazz = file.baseName
  val domainClasses = 
    routesByDomain.keys.map { DOMAIN_ROUTES(_).baseName }
  val data =
    s"""package info.vizierdb.api.akka
       |$AUTOGEN_HEADER
       |import akka.http.scaladsl.model._
       |import akka.http.scaladsl.server.Directives._
       |import akka.http.scaladsl.model.headers.`Content-Type`
       |import akka.http.scaladsl.model.ContentTypes._
       |import akka.http.scaladsl.model.HttpHeader
       |
       |object $clazz 
       |{
       |  val routes = concat(
       |    ${domainClasses.map { _ + ".routes"}.mkString(",\n    ") }
       |  )
       |}
       """.stripMargin
  os.write.over(file, data)
  routesByDomain.keys
}

///////////////////////////////////////////////////////
////////////////  Websocket Proxy   ///////////////////
///////////////////////////////////////////////////////

val WEBSOCKET_INTERNAL_ARGS = Set("projectId", "branchId")
val WEBSOCKET_ROUTES =
    ROUTES.filter { r => (r.domain == "workflow") && 
                         (r.action.startsWith("head_")) }
val UNDEFOR = "UndefOr\\[([^\\]]+)\\]".r

def renderWebsocketRouteHandler(route: Route): (String, String) = 
{
  val action = route.actionLabel.replace("Head", "")

  val parameters = 
    route.allParams
         .map { 
            case Param(name, "FILE") => 
              Param(name, "(Array[Byte], String)")
            case Param(name, UNDEFOR(body)) => 
              Param(name, s"Option[$body]")
            case x => x
          }

  val request =
    parameters.filter { x => WEBSOCKET_INTERNAL_ARGS.contains(x.identifier) }
              .map { x => s"${x.identifier} = ${x.identifier}"}

  val internal =
    parameters.filterNot { x => WEBSOCKET_INTERNAL_ARGS.contains(x.identifier) }
              .map { x => s"${x.identifier} = args(\"${x.identifier}\").as[${x.scalaType}]" }

  val callString =
    (request ++ internal).mkString(", ")

  val fieldString =
    parameters.filterNot { x => WEBSOCKET_INTERNAL_ARGS.contains(x.identifier) }
              .map { _.typedIdentifier }.mkString(", ")

  val jsonString =
    parameters.filterNot { x => WEBSOCKET_INTERNAL_ARGS.contains(x.identifier) }
              .map { p =>
                s"\"${p.identifier}\" -> Json.toJson(${p.identifier})"
              }
              .mkString(", ")

  (
    s"""    case "${action}" => 
       |      Json.toJson(${route.handler}(${callString}):${route.returns})
       """.stripMargin,
    s"""  def ${action}(${fieldString}): Future[${route.returns}] =
     |    sendRequest(Seq("${action}"), Map(${jsonString}))
     |       .map { _.as[${route.returns}] }
     """.stripMargin
  )
}

{
  val file = WEBSOCKET_IMPL

  val data: String = 
    s"""package info.vizierdb.api.websocket
       |$AUTOGEN_HEADER
       |import play.api.libs.json._
       |import info.vizierdb.types._
       |import info.vizierdb.api.handler._
       |import info.vizierdb.api._
       |import info.vizierdb.serialized
       |import info.vizierdb.serializers._
       |import info.vizierdb.spark.caveats.DataContainer
       |
       |abstract class BranchWatcherAPIRoutes
       |{
       |  implicit def liftToOption[T](x: T): Option[T] = Some(x)
       |  def projectId: Identifier
       |  def branchId: Identifier
       |
       |  def route(path: Seq[String], args: Map[String, JsValue]): JsValue =
       |    path.last match {
       |${WEBSOCKET_ROUTES.map { renderWebsocketRouteHandler(_)._1 }
                          .mkString("\n")}
       |    }
       |}
       """.stripMargin

  os.write.over(file, data)
}

{
  val file = WEBSOCKET_PROXY

  val data: String = 
    s"""package info.vizierdb.ui.network
       |$AUTOGEN_HEADER
       |import play.api.libs.json._
       |import info.vizierdb.types._
       |import info.vizierdb.serialized
       |import info.vizierdb.serializers._
       |import scala.concurrent.Future
       |import info.vizierdb.spark.caveats.DataContainer
       |import scala.concurrent.ExecutionContext.Implicits.global
       |
       |abstract class BranchWatcherAPIProxy
       |{
       |  def sendRequest(leafPath: Seq[String], args: Map[String, JsValue]): Future[JsValue]
       |
       |${WEBSOCKET_ROUTES.map { renderWebsocketRouteHandler(_)._2 }
                          .mkString("\n")}
       |}
       """.stripMargin

  os.write.over(file, data)
}

///////////////////////////////////////////////////////
////////////  Websocket API Frontend  /////////////////
///////////////////////////////////////////////////////

def websocketAPICall(route: Route): String =
{
  val routeString = 
    route.path.map { 
      case PathLiteral(lit) => lit
      case PathVariable(_, t) => s"{{${t.toString}}}"
    }.mkString("/")

  val urlPathParams =
    route.pathParams.map { p => s"${p.identifier}:${p.nativeScalaType}" } ++
    route.pathQueryParams.map { p => s"${p.identifier}:Option[${p.scalaType}] = None" }

  val urlInvocation = 
    route.pathParams.map { p => s"${p.identifier}" } ++
    route.pathQueryParams.map { p => s"${p.identifier}" }

  val pathFormat =
    (Seq(
      "s\""+route.path.map {
        case PathLiteral(lit) => lit
        case PathVariable(id, _) => "${"+id+"}"
      }.mkString("/")+"\""
    ) ++ route.pathQueryParams.map { p =>
      s""""${p.identifier}" -> ${p.identifier}.map { _.toString }"""
    }).mkString(", ")

  val ajaxArgs = 
    Seq("url = url") ++
    (if(route.jsonParams.isEmpty) { Seq() } else {
      Seq("data = Json.obj(\n"+
        route.jsonParams.map { p =>
          s"""  "${p.identifier}" -> ${p.identifier},"""
        }.mkString("\n")+"\n).toString"
      )
    })

  val allParams = 
    route.pathParams.map { _.typedNativeIdentifier } ++
    route.jsonParams.map { p => p.typedNativeIdentifier + (if(p.isOptional) { "= None" } else { "" }) } ++
    route.pathQueryParams.map { p => s"${p.identifier}:Option[${p.scalaType}] = None" }

  val url = 
    s"""  def ${route.actionLabel}URL(${urlPathParams.map { "\n    " + _ }.mkString(",")}${if(urlPathParams.size > 0){"\n  "} else { "" }}): String =
       |    makeUrl(${pathFormat})
       """.stripMargin
  
  val shouldIncludeBase = 
    !route.allParams.exists { _.scalaType.startsWith("FILE") } &&
    !route.returns.startsWith("FILE")

  val base = 
    if(shouldIncludeBase){
      s"""  def ${route.actionLabel}(
         |${allParams.map { "    "+_ }.mkString(", \n")}
         |  ): ${if(route.returns == "Unit") { "Unit" } else { s"Future[${route.returns}]" }} =
         |  {
         |    val url = ${route.actionLabel}URL(${urlInvocation.mkString(", ")})
         |    Ajax.${route.verb.toLowerCase}(
         |${ajaxArgs.map { "      "+_ }.mkString(",\n")}
         |    )${if(route.returns == "Unit") { "" } 
                 else { s".map { xhr => \n      Json.parse(xhr.responseText)\n          .as[${route.returns}]\n    }" }}
         |  }
         """.stripMargin
    } else { "" }


  s"/** ${route.verb} ${routeString} **/\n${url}\n${base}"
}

{
  val file = API_PROXY

  val data: String =
    s"""package info.vizierdb.ui.network
       |import scala.scalajs.js
       |import play.api.libs.json._
       |import org.scalajs.dom.ext.Ajax
       |
       |import info.vizierdb.types._
       |import scala.concurrent.Future
       |import scala.concurrent.ExecutionContext.Implicits.global
       |
       |import info.vizierdb.serialized
       |import info.vizierdb.ui.components.Parameter
       |import info.vizierdb.util.Logging
       |import info.vizierdb.serializers._
       |import info.vizierdb.spark.caveats.DataContainer
       |import info.vizierdb.nativeTypes.Caveat
       |
       |case class API(baseUrl: String)
       |  extends Object
       |  with Logging
       |  with APIExtras
       |{
       |
       |  def makeUrl(path: String, query: (String, Option[String])*): String = 
       |    baseUrl + path + (
       |      if(query.forall { _._2.isEmpty }) { "" }
       |      else { 
       |        "?" + query.collect { 
       |                case (k, Some(v)) => k + "=" + v 
       |              }.mkString("&") 
       |      }
       |    )
       |${ROUTES.map { websocketAPICall(_) }.mkString("\n")}
       |}
       """.stripMargin

  os.write.over(file, data)
}