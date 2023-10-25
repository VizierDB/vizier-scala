import scala.collection.mutable
import $ivy.`com.typesafe.play::play-json:2.10.0-RC7`, play.api.libs.json._
import $ivy.`io.swagger:swagger-codegen:2.4.27`, io.swagger
import $file.lib.regexp, regexp.{ UNDEFOR, IS_FILE, SEQ, SERIALIZED }
import $file.lib.routes, routes._


// Utilities for processing vizier-routes.txt into source code

val SOURCE_DIR   = os.pwd / "vizier"
val BACKEND_DIR  = SOURCE_DIR / "backend" / "src" / "info" / "vizierdb"
val UI_DIR       = SOURCE_DIR / "ui"      / "src" / "info" / "vizierdb"
val RESOURCE_DIR = SOURCE_DIR / "resources"
val INPUT        = RESOURCE_DIR / "vizier-routes.txt"

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

val SWAGGER_BASE = RESOURCE_DIR / "swagger"
val SWAGGER_FILE = SWAGGER_BASE / "vizier-api.json"

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

val ROUTES: Seq[Route] = readRoutes(INPUT)

///////////////////////////////////////////////////////
//////////////////  Akka Routes   /////////////////////
///////////////////////////////////////////////////////

import $file.lib.akkagen, akkagen.{ AkkaDirective, AkkaConcat }

def akkaRouteHandler(routes: Seq[Route]): String =
{

  val handlers =
    routes.map { route => 
      var handler: AkkaDirective = akkagen.baseHandler(route)

      handler = akkagen.decodeBody(route, handler)

      handler = handler.push(s"${route.verb.toLowerCase()} {")

      /* return */ handler
    }

  var handler: AkkaDirective = 
    handlers match {
      case Seq(x) => x
      case x => AkkaConcat(x)
    }

  handler = akkagen.decodePathQuery(routes.head, handler)
  handler = akkagen.decodePath(routes.head, handler)

  s"  val ${routes.head.action}_route =\n${handler.render("    ", Seq.empty)}"
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

  val routesByPath = 
    routes.groupBy { _.path.toString }
          .map { _._2 }
          .toIndexedSeq
  val data = 
    s"""package info.vizierdb.api.akka
       |$AUTOGEN_HEADER
       |import play.api.libs.json._
       |import akka.http.scaladsl.model._
       |import akka.http.scaladsl.server.Directives._
       |import akka.http.scaladsl.model.headers.`Content-Type`
       |import akka.http.scaladsl.model.HttpHeader
       |import akka.http.scaladsl.model.HttpEntity
       |import akka.http.scaladsl.unmarshalling._
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
       |${routesByPath
                .map { akkaRouteHandler(_) }
                .mkString("\n\n")}
       |
       |  val routes = concat(
       |    ${routesByPath.map { routes => s"${routes.head.action}_route" }.mkString(",\n    ")}
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
                         (r.action.startsWith("head_")) &&
                         (r.action != "head_graph") }

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
       |      Json.toJson(${route.handler}(${callString}):${route.returnsAsScalaType})
       """.stripMargin,
    s"""  def ${action}(${fieldString}): Future[${route.returnsAsScalaType}] =
     |    sendRequest(Seq("${action}"), Map(${jsonString}))
     |       .map { _.as[${route.returnsAsScalaType}] }
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
      "s\"/"+route.path.map {
        case PathLiteral(lit) => lit
        case PathVariable(id, _) => "${"+id+"}"
      }.mkString("/")+"\""
    ) ++ route.pathQueryParams.map { p =>
      s""""${p.identifier}" -> ${p.identifier}.map { _.toString }"""
    }).mkString(", ")

  val ajaxArgs = 
    Seq("url = url") ++ (
    route.body match {
      case EmptyBody => Seq()
      case JsonParamBody(params) => 
        Seq(
          """headers = Map("Content-Type" -> "application/json")""",
          "data = Json.obj(\n"+
          params.map { p =>
            s"""  "${p.identifier}" -> ${p.identifier},"""
          }.mkString("\n")+"\n).toString"
        )
      case RawJsonBody(identifier) =>
        Seq(
          """headers = Map("Content-Type" -> "application/json")""",
          s"data = $identifier.toString"
        )
      case RawStringBody(identifier) =>
        Seq(
          """headers = Map("Content-Type" -> "text/plain")""",
          s"data = $identifier"
        )
      case FileBody(identifier) =>
        Seq(
          """headers = Map("Content-Type" -> "application/octet-stream")""",
          s"data = $identifier"
        )
    })

  val allParams = 
    route.pathParams.map { _.typedNativeIdentifier } ++
    route.bodyParams.map { p => p.typedNativeIdentifier + (if(p.isOptional) { "= None" } else { "" }) } ++
    route.pathQueryParams.map { p => s"${p.identifier}:Option[${p.scalaType}] = None" }

  val url = 
    s"""  def ${route.actionLabel}URL(${urlPathParams.map { "\n    " + _ }.mkString(",")}${if(urlPathParams.size > 0){"\n  "} else { "" }}): String =
       |    makeUrl(${pathFormat})
       """.stripMargin
  
  val shouldIncludeBase = 
    !route.allParams.exists { _.scalaType.startsWith("FILE") } &&
    !route.returnsAsScalaType.startsWith("FILE")

  val base = 
    if(shouldIncludeBase){
      s"""  def ${route.actionLabel}(
         |${allParams.map { "    "+_ }.mkString(", \n")}
         |  ): ${if(route.returnsAsScalaType == "Unit") { "Unit" } else { s"Future[${route.returnsAsScalaType}]" }} =
         |  {
         |    val url = ${route.actionLabel}URL(${urlInvocation.mkString(", ")})
         |    Ajax.${route.verb.toLowerCase}(
         |${ajaxArgs.map { "      "+_ }.mkString(",\n")}
         |    )${if(route.returnsAsScalaType == "Unit") { "" } 
                 else { 
                  s".recover { case AjaxException(req) => checkError(req) }\n     "++
                  s".map { xhr => \n      Json.parse(xhr.responseText)\n          .as[${route.returnsAsScalaType}]\n    }" 
                }}
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
       |import org.scalajs.dom.ext.AjaxException
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

///////////////////////////////////////////////////////
/////////////////  Swagger API  ///////////////////////
///////////////////////////////////////////////////////

{
  def dataTypeToSwaggerType(dataType: String): String =
    dataType match {
      case "long"     => "integer" 
      case "int"      => "integer"
      case "subpath"  => "string"
      case "string"   => "string"

    }


  def scalaTypeToMimeType(scalaType: String): String =
    scalaType match {
      case IS_FILE("*") => "application/octet-stream"
      case IS_FILE(mime) => mime
      case _ => "application/json"
    }

  val definitions = mutable.Map[String, JsValue]()

  def typeRef(t: String): JsObject =
  {
    assert(definitions contains t, s"Unimplemented type schema $t")
    Json.obj("$ref" -> s"#/definitions/$t")
  }


  def define(t: (String, JsObject)): Unit =
    definitions.put(t._1, t._2)

  def mkPrim(t: String) = Json.obj("type" -> t)

  def mkObject(opt: Set[String] = Set.empty, extra: JsValue = JsNull)(props: (String, JsValue)*) =
    Json.obj(
      "type" -> "object",
      "required" -> JsArray(props.map { _._1 }.filterNot { opt contains _ }.map { JsString(_) }),
      "properties" -> JsObject(props.toMap),
      "additionalProperties" -> extra,
    )

  def mkArray(elem: JsValue) = Json.obj("type" -> "array", "items" -> elem)

  define { "Identifier" -> Json.obj(
    "type" -> "string",
    "description" -> "An opaque identifier"
  )}
  define { "CommandArgument" -> mkObject()(
    "id" -> mkPrim("string"),
    "value" -> Json.obj(),
  )}
  define { "Property" -> mkObject()(
    "key" -> mkPrim("string"),
    "value" -> Json.obj(),
  )}
  define { "CellDataType" -> Json.obj(
    "type" -> "string",
    "description" -> "A Vizier-serialized spark data type.  See https://github.com/VizierDB/vizier-scala/blob/v2.0/vizier/backend/src/info/vizierdb/spark/SparkSchema.scala"
  )}
  define { "DatasetColumn" -> mkObject()(
    "id" -> Json.obj("type" -> "integer"),
    "name" -> Json.obj("type" -> "string"),
    "type" -> typeRef("CellDataType")
  )}
  define { "RowIdentifier" -> Json.obj(
    "type" -> "string",
    "description" -> "An opaque, persistent, unique identifier for this row"
  )}
  define { "DatasetRow" -> mkObject(opt = Set("rowAnnotationFlags", "rowIsAnnotated"))(
    "id" -> typeRef("RowIdentifier"),
    "values" -> mkArray(Json.obj()),
    "rowAnnotationFlags" -> mkArray(mkPrim("boolean")),
    "rowIsAnnotated" -> mkPrim("boolean")
  )}
  define { "DatasetAnnotation" -> mkObject()(
    "columnId" -> mkPrim("integer"),
    "rowId" -> typeRef("RowIdentifier"),
    "key" -> mkPrim("string"),
    "value" -> mkPrim("string")
  )}
  define { "BranchSource" -> mkObject(opt = Set("workflowId", "moduleId"))(
    "branchId"   -> typeRef("Identifier"),
    "workflowId" -> typeRef("Identifier"),
    "moduleId"   -> typeRef("Identifier")
  )}
  define { "JsValue" -> Json.obj(
    "type" -> "any",
    "description" -> "the value, encoded as Json"
  )}
  define { "PythonPackage" -> mkObject()(
    "name" -> Json.obj("type" -> "string"),
    "version" -> Json.obj("type" -> "string"),
  )}
  define { "PythonEnvironmentDescriptor" -> mkObject()(
    "version" -> Json.obj("type" -> "string"),
    "packages" -> mkArray(typeRef("PythonPackage")),
  )}

  val aliases = Map(
    "CommandArgumentList.T" -> "Seq[CommandArgument]",
    "PropertyList.T" -> "Seq[Property]",
  )

  def scalaTypeToSchema(scalaType: String): JsObject =
  {
    scalaType match {
      case t if aliases contains t => scalaTypeToSchema(aliases(t))
      case ref if definitions contains ref => typeRef(ref)
      case SERIALIZED(t) => scalaTypeToSchema(t)
      case UNDEFOR(elem) => scalaTypeToSchema(elem)
      case SEQ(elem) => Json.obj("type" -> "array", "items" -> scalaTypeToSchema(elem))
      case "String" => Json.obj("type" -> "string")
      case "JSON" => Json.obj("type" -> "object", "additionalProperties" -> true)
    }
  }

  val paths = 
    JsObject(
      ROUTES.groupBy { "/"+_.pathString }
            .toSeq.sortBy { _._1 }
            .map { case (path, routes) => path -> 
              JsObject(
                routes.map { route => route.verb.toLowerCase -> 
                    Json.obj(
                      "operationId" -> route.actionLabel,
                      "produces" -> Json.arr(
                        scalaTypeToMimeType(route.returns)
                      ),
                      "tags" -> Json.arr(
                        route.domain
                      ),
                      "parameters" -> JsArray(
                        (
                          route.body match {
                            case EmptyBody => Seq.empty
                            case JsonParamBody(params) => 
                              Seq(Json.obj(
                                "name" -> "Body",
                                "in" -> "body",
                                "required" -> true,
                                "schema" -> Json.obj(
                                  "type" -> "object",
                                  "required" -> JsArray(
                                    params.filterNot { _.isOptional }
                                          .map { _.identifier }
                                          .map { JsString(_) }
                                  ),
                                  "properties" -> JsObject(
                                    params.map { p =>
                                      p.identifier -> 
                                        scalaTypeToSchema(p.scalaType)
                                    }.toMap
                                  )
                                )
                              ))
                            case RawJsonBody(label) =>
                              Seq(Json.obj(
                                "name" -> label,
                                "in" -> "body",
                                "required" -> true,
                                "schema" -> Json.obj(
                                  // "type" -> "any"
                                )
                              ))
                            case RawStringBody(label) =>
                              Seq(Json.obj(
                                "name" -> label,
                                "in" -> "body",
                                "required" -> true,
                                "schema" -> Json.obj(
                                  "type" -> "string"
                                )
                              ))
                            case FileBody(label) =>
                              Seq(Json.obj(
                                "name" -> label,
                                "in" -> "body",
                                "required" -> true,
                                "schema" -> Json.obj(
                                  "type" -> "file"
                                )
                              ))
                          }
                        ) ++
                        route.pathQueryParams.map { p => 
                          Json.obj(
                            "name" -> p.identifier,
                            "in" -> "query",
                            "required" -> false,
                            "type" -> dataTypeToSwaggerType(p.dataType)
                          )
                        }
                      )
                    )
                  }
                  .toMap ++ 
                Map(
                  "parameters" -> JsArray(
                    routes.head.path.collect {
                      case PathVariable(id, dataType) => 
                        Json.obj(
                          "name" -> id,
                          "in" -> "path",
                          "required" -> true,
                          "type" -> dataTypeToSwaggerType(dataType)
                        )
                    }
                  )
                )
              )

            }
    )

  val info = Json.obj(
    "title" -> "Vizier DB",
    "description" -> "A 'data-centric' microkernel notebook",
    "version" -> "v1.1",
    "contact" -> Json.obj(
      "name" -> "University at Buffalo, Illinois Institute of Technology, New York University, Breadcrumb Analytics",
      "url" -> "https://vizierdb.info",
      "email" -> "info@vizierdb.info"
    ),
  )

  val root = Json.obj(
    "swagger" -> "2.0",
    "basePath" -> "vizier-db/api/v1",
    "info" -> info,
    "paths" -> paths,
    "host" -> "localhost:5000/",
    "schemes" -> "http",
    "definitions" -> definitions,
  )

  val spec = Json.prettyPrint(root)

  os.makeDir.all(SWAGGER_BASE)
  os.write.over(SWAGGER_FILE, spec)

  /////////// Doc file /////////////

  val configurator = new swagger.codegen.config.CodegenConfigurator()

  configurator.setLang("html2")
  configurator.setInputSpec(SWAGGER_FILE.toString)
  configurator.setOutputDir(SWAGGER_BASE.toString)
  configurator.setIgnoreFileOverride("./.swagger-codegen-ignore")

  // input.setSwagger

  new swagger.codegen.DefaultGenerator()
             .opts(configurator.toClientOptInput())
             .generate

}
