# -- copyright-header:v2 --
# Copyright (C) 2017-2021 University at Buffalo,
#                         New York University,
#                         Illinois Institute of Technology.
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#     http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# -- copyright-header:end --
from typing import cast, List, Dict, Tuple
import re
import sys

INPUT_FILE = "vizier/resources/vizier-routes.txt"
SERVLET_FILE = "vizier/src/info/vizierdb/api/servlet/VizierAPIServletRoutes.scala"
WEBSOCKET_IMPL_FILE = "vizier/src/info/vizierdb/api/websocket/BranchWatcherAPIRoutes.scala"
WEBSOCKET_PROXY_FILE = "vizier/ui/src/info/vizierdb/ui/network/BranchWatcherAPIProxy.scala"
API_PROXY_FILE = "vizier/ui/src/info/vizierdb/ui/network/API.scala"

with open(INPUT_FILE) as f:
  routes = [
    (idx, path, verb, handler, group, action, return_type,
      [ 
        (name, dataType)
        for arg in re.split(";", body_args)
        for (name, dataType) in [arg.split(":")]
      ] if body_args != "_" else []
    )
    for idx, line in enumerate(f.readlines())
    for path, verb, group, action, handler, return_type, body_args in [re.split(" +", line.rstrip())]
  ]

matcherDefinitions: List[str] = []
verbHandlers: Dict[str, List[str]] = {}
preflightPaths: Dict[str, Tuple[List[str], str]] = {}


DATATYPE_OPERATIONS = {
  "int": ("([0-9]+)", "{}.toInt", "Int"),
  "long": ("([0-9]+)", "{}.toLong", "Long"),
  "subpath": ("(.*)", None, "String"),
  "string": ("([^/]+)", None, "String"),
}


def scala_type_for_field_type(fieldType: str) -> str:
  if fieldType not in DATATYPE_OPERATIONS:
    raise ValueError(f"Unsupported field type: {fieldType}")
  return DATATYPE_OPERATIONS[fieldType][2]


def extract_path_fields(path: str) -> Tuple[List[Tuple[str, str, str]], List[str], List[Tuple[str, str]]]:
  if path.find("?") >= 0:
    path, rawQuery = path.split("?")
    query: List[Tuple[str, str]] = [ 
      (name, dataType)
      for element in rawQuery.split("&")
      for (name, dataType) in [element.split(":")]
    ]
  else:
    query = []

  pathComponents = path.split("/")
  regexp: List[str] = []
  fields: List[Tuple[str, str, str]] = []
  for component in pathComponents:
    match = re.match("\\{([^}:]+):([^}]+)\\}", component)
    if match is None:
      regexp += [component]
    else:
      fieldName = match.group(1)
      fieldType = match.group(2)
      if fieldType not in DATATYPE_OPERATIONS:
        raise ValueError("Unsupported field type: {} in {}".format(
                          fieldType, "/".join(pathComponents)))
      fieldRegexp, fieldExtractor, fieldTypeBase = DATATYPE_OPERATIONS[fieldType]
      regexp += [fieldRegexp]
      fields += [(fieldName, fieldExtractor.format(fieldName) if fieldExtractor is not None else fieldName, fieldTypeBase)]
  return (fields, regexp, query)


def format_arg_constructor(fields: List[Tuple[str, str]]) -> str:
  return ", ".join(
    "{} = {}".format(fieldName, fieldEncoder)
    for fieldName, fieldEncoder in fields
  )


def get_query_extractor(fieldName, fieldType) -> str:
  if fieldType not in DATATYPE_OPERATIONS:
    raise ValueError("Unsupported field type: {} ({})".format(
                      fieldType, fieldName))
  regexp, fieldExtractor, fieldTypeBase = DATATYPE_OPERATIONS[fieldType]
  return "".join([
    f"queryParameter(connection, \"{fieldName}\")",
    (".map { " + fieldExtractor.format("_") + " }" if fieldExtractor is not None else "")
  ])


def get_body_extractor(fieldName, fieldType) -> str:
  if(fieldType == "FILE"):
    return f"connection.getPart(\"{fieldName}\")"
  else:
    castOperator = "as"
    if fieldType.startswith("UndefOr["):
      fieldType = fieldType[8:-1]
      castOperator = "asOpt"
    # fieldType = fieldType.replace("Identifier", "Long")
    return f"(jsonBody \\ \"{fieldName}\").{castOperator}[{fieldType}]"


def path_format_string(path: str) -> Tuple[str, List[str]]:
  if path.find("?") >= 0:
    path, query = path.split("?")
    query_vars = [
      q.split(":")[0]
      for q in query.split("&")
    ]
  else:
    query_vars = []

  path = "/".join(
    "${" + element[1:-1].split(":")[0] + "}" if element.startswith("{") else element
    for element in path.split("/")
  )

  return (path, query_vars)


# ##################### Servlet ######################

for idx, path, verb, handler, group, action, return_type, body_fields in routes:
  path_fields_with_scala, regexp, query_fields = extract_path_fields(path)

  if len(path_fields_with_scala) == 0:
    matcher = "\"" + path + "\""
  else:
    matcher = "ROUTE_PATTERN_{}({})".format(
                idx,
                ", ".join(fieldName for fieldName, fieldEncoder, fieldTypeBase in path_fields_with_scala)
              )
    matcherDefinitions += [
      "val ROUTE_PATTERN_{} = \"{}\".r".format(
        idx,
        "/".join(regexp)
      )
    ]

  path_fields = [
    (fieldName, fieldEncoder)
    for fieldName, fieldEncoder, fieldTypeBase in path_fields_with_scala
  ]

  query_fields = [
    (fieldName, get_query_extractor(fieldName, fieldType))
    for (fieldName, fieldType) in query_fields
  ]

  body_fields = [
    (fieldName, get_body_extractor(fieldName, fieldType))
    for (fieldName, fieldType) in body_fields
  ]

  all_fields = path_fields + query_fields + body_fields

  invokeHandler = f"{handler}({format_arg_constructor(all_fields)})"

  if return_type.startswith("serialized.") or return_type.startswith("Seq[serialized.") or return_type == "DataContainer" or return_type == "Seq[Caveat]":
    invokeHandler = f"RawJsonResponse(Json.toJson({invokeHandler}:{return_type}))"
  elif return_type.startswith("FILE"):
    pass
  elif return_type == "Unit":
    invokeHandler = invokeHandler + "; NoContentResponse()"
  else:
    raise Exception(f"Unsupported return type {return_type}")
  
  invokeHandler = f"case {matcher} => {invokeHandler}"

  if verb not in verbHandlers:
    verbHandlers[verb] = cast(List[str], [])
  verbHandlers[verb] += [invokeHandler]

  if path not in preflightPaths:
    preflightPaths[path] = ([], matcher)
  preflightPaths[path][0].append(verb)

original_stdout = sys.stdout
with open(SERVLET_FILE, "w") as f:
  sys.stdout = f
  print("package info.vizierdb.api.servlet")
  print("")
  print("/* this file is AUTOGENERATED by `scripts/build_routes` from `{}` */".format(INPUT_FILE))
  print("/* DO NOT EDIT THIS FILE DIRECTLY */")
  print("")
  print("import play.api.libs.json._")
  print("import java.net.URLDecoder")
  print("import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}")
  print("import java.net.URLDecoder")
  print("import info.vizierdb.api.Response")
  print("import info.vizierdb.spark.caveats.DataContainer")
  print("import info.vizierdb.spark.caveats.CaveatFormat._")
  print("import org.mimirdb.caveats.Caveat")
  print("import info.vizierdb.types._")
  print("import info.vizierdb.api._")
  print("import info.vizierdb.api.response.{ CORSPreflightResponse, RawJsonResponse, NoContentResponse }")
  print("import info.vizierdb.api.handler._")
  print("import info.vizierdb.serialized")
  print("import info.vizierdb.serializers._")
  print("")
  print("trait VizierAPIServletRoutes extends HttpServlet {")
  print("")
  print("  def processResponse(request: HttpServletRequest, output: HttpServletResponse)(response: => Response): Unit")
  print("  def fourOhFour(response: HttpServletRequest): Response")
  print("  implicit def liftToOption[T](x: T): Option[T] = Some(x)")
  print("  def queryParameter(connection: JettyClientConnection, name:String): Option[String] =")
  print("    Option(connection.getParameter(name)).map { URLDecoder.decode(_, \"UTF-8\") }")
  print("")

  print("\n".join("  " + matcher for matcher in matcherDefinitions))

  print("")
  for verb in verbHandlers:
    capsedVerb = verb[0].upper() + verb[1:].lower()
    print("  override def do{}(request: HttpServletRequest, response: HttpServletResponse) = ".format(capsedVerb))
    print("  {")
    print("    val connection = new JettyClientConnection(request, response)")
    print("    lazy val jsonBody = connection.getJson.as[JsObject]")
    print("    processResponse(request, response) {")
    print("      request.getPathInfo match {")
    for handler in verbHandlers[verb]:
      print("        " + handler)
    print("        case _ => fourOhFour(request)")
    print("      }")
    print("    }")
    print("  }")

  print("")
  print("  override def doOptions(request: HttpServletRequest, response: HttpServletResponse) = ")
  print("  {")
  print("    val connection = new JettyClientConnection(request, response)")
  print("    processResponse(request, response) {")
  print("      request.getPathInfo match {")
  for preflightPath in preflightPaths:
    verbs, matcher = preflightPaths[preflightPath]
    print("        case {} => CORSPreflightResponse({})".format(
            matcher,
            ", ".join("\"" + verb + "\"" for verb in verbs)
          ))
  print("        case _ => fourOhFour(request)")
  print("      }")
  print("    }")
  print("  }")
  print("}")
sys.stdout = original_stdout

# ##################### Websocket ######################

WEBSOCKET_INTERNAL_ARGS = set(["projectId", "branchId"])

with open(WEBSOCKET_IMPL_FILE, "w") as f:
  sys.stdout = f
  print("package info.vizierdb.api.websocket")
  print("")
  print("/* this file is AUTOGENERATED by `scripts/build_routes` from `{}` */".format(INPUT_FILE))
  print("/* DO NOT EDIT THIS FILE DIRECTLY */")
  print("")
  print("import play.api.libs.json._")
  print("import info.vizierdb.types._")
  print("import info.vizierdb.api.handler._")
  print("import info.vizierdb.api._")
  print("import info.vizierdb.serialized")
  print("import info.vizierdb.serializers._")
  print("import info.vizierdb.spark.caveats.DataContainer")
  print("")
  print("abstract class BranchWatcherAPIRoutes")
  print("{")
  print("  implicit def liftToOption[T](x: T): Option[T] = Some(x)")
  print("  def projectId: Identifier")
  print("  def branchId: Identifier")
  print("")
  print("  def route(path: Seq[String], args: Map[String, JsValue]): JsValue =")
  print("    path.last match {")

  for idx, path, verb, handler, group, action, return_type, body_fields in routes:
    if group != "workflow" or not (action.startswith("head_")):
      continue
    
    # prefix with group and convert to camelCase
    action = group + "".join([
      word.capitalize()
      for word in action.replace("head_", "").split("_")
    ])

    path_fields_with_scala, regexp, query_fields = extract_path_fields(path)

    fields_with_types = [
      (fieldName, fieldTypeBase)
      for (fieldName, fieldType, fieldTypeBase) in path_fields_with_scala
    ] + [
      (fieldName, scala_type_for_field_type(fieldType))
      for (fieldName, fieldType) in query_fields
    ] + [
      (fieldName, fieldType.replace("FILE", "(Array[Byte], String)"))
      for (fieldName, fieldType) in body_fields
    ]

    fields_with_types = [
      (fieldName, fieldType.replace("UndefOr[", "Option["))
      for fieldName, fieldType in fields_with_types
    ]
    
    internalArgs = [
      f"{arg} = {arg}"
      for arg in WEBSOCKET_INTERNAL_ARGS
    ]
    requestArgs = [
      f"{fieldName} = args(\"{fieldName}\").as[{fieldType}]"
      for fieldName, fieldType in fields_with_types
      if fieldName not in WEBSOCKET_INTERNAL_ARGS
    ]
    callString = ", ".join(internalArgs + requestArgs)

    print(f"      case \"{action}\" => Json.toJson({handler}({callString}):{return_type})")
  print("    }")
  print("}")
sys.stdout = original_stdout

with open(WEBSOCKET_PROXY_FILE, "w") as f:
  sys.stdout = f
  print("package info.vizierdb.ui.network")
  print("")
  print("/* this file is AUTOGENERATED by `scripts/build_routes` from `{}` */".format(INPUT_FILE))
  print("/* DO NOT EDIT THIS FILE DIRECTLY */")
  print("")
  print("import play.api.libs.json._")
  print("import info.vizierdb.types._")
  print("import info.vizierdb.serialized")
  print("import info.vizierdb.serializers._")
  print("import scala.concurrent.Future")
  print("import info.vizierdb.spark.caveats.DataContainer")
  print("import scala.concurrent.ExecutionContext.Implicits.global")
  print("")
  print("abstract class BranchWatcherAPIProxy")
  print("{")
  print("  def sendRequest(leafPath: Seq[String], args: Map[String, JsValue]): Future[JsValue]")
  print("")

  for idx, path, verb, handler, group, action, return_type, body_fields in routes:
    if group != "workflow" or not (action.startswith("head_")):
      continue
    
    # prefix with group and convert to camelCase
    action = group + "".join([
      word.capitalize()
      for word in action.replace("head_", "").split("_")
    ])

    path_fields_with_scala, regexp, query_fields = extract_path_fields(path)

    fields_with_types = [
      (fieldName, fieldTypeBase)
      for (fieldName, fieldType, fieldTypeBase) in path_fields_with_scala
    ] + [
      (fieldName, scala_type_for_field_type(fieldType))
      for (fieldName, fieldType) in query_fields
    ] + [
      (fieldName, fieldType.replace("FILE", "(Array[Byte], String)"))
      for (fieldName, fieldType) in body_fields
    ]

    fields_with_types = [
      (fieldName, fieldType.replace("UndefOr[", "Option["))
      for fieldName, fieldType in fields_with_types
    ]

    fieldString = ", ".join(
      fieldName + ":" + fieldType
      for fieldName, fieldType in fields_with_types
      if fieldName not in WEBSOCKET_INTERNAL_ARGS
    )
    callString = ", ".join(
      "\"" + fieldName + "\" -> Json.toJson(" + fieldName + ")"
      for fieldName, fieldType in fields_with_types
      if fieldName not in WEBSOCKET_INTERNAL_ARGS
    )

    print(f"  def {action}({fieldString}): Future[{return_type}] =")
    print(f"    sendRequest(Seq(\"{action}\"), Map({callString}))")
    print("       .map { _.as[" + return_type + "] }")
  print("}")
sys.stdout = original_stdout

# ##################### Frontend API ######################
with open(API_PROXY_FILE, "w") as f:
  sys.stdout = f
  print("package info.vizierdb.ui.network")
  print("")
  print("/* this file is AUTOGENERATED by `scripts/build_routes` from `{}` */".format(INPUT_FILE))
  print("/* DO NOT EDIT THIS FILE DIRECTLY */")
  print("")
  print("import scala.scalajs.js")
  print("import play.api.libs.json._")
  print("import org.scalajs.dom.ext.Ajax")
  print("")
  print("import info.vizierdb.types._")
  print("import scala.concurrent.Future")
  print("import scala.concurrent.ExecutionContext.Implicits.global")
  print("")
  print("import info.vizierdb.serialized")
  print("import info.vizierdb.ui.components.Parameter")
  print("import info.vizierdb.util.Logging")
  print("import info.vizierdb.serializers._")
  print("import info.vizierdb.spark.caveats.DataContainer")
  print("import info.vizierdb.nativeTypes.Caveat")
  print("")
  print("case class API(baseUrl: String)")
  print("  extends Object")
  print("  with Logging")
  print("  with APIExtras")
  print("{")
  print("")
  print("  def makeUrl(path: String, query: (String, Option[String])*): String = ")
  print("    baseUrl + path + (")
  print("      if(query.isEmpty) { \"\" }")
  print("      else{ \"?\" + query.collect { case (k, Some(v)) => k + \"=\" + v }.mkString(\"&\") }")
  print("    )")
  print("")
  for idx, path, verb, handler, group, action, return_type, body_fields in routes:
    supported = True
    for (field, dataType) in body_fields:
      if(dataType == "FILE"): 
        supported = False
    if return_type.startswith("FILE"):
      supported = False
    if supported:
      action = group + "".join([
        word.capitalize()
        for word in action.split("_")
      ])
      path_fields_with_scala, regexp, query_fields = extract_path_fields(path)
      print(f"  /** {verb} {path} **/")
      print(f"  def {action}(")
      for (fieldName, fieldType, fieldTypeBase) in path_fields_with_scala:
        print(f"    {fieldName}:{fieldTypeBase},")
      for (fieldName, fieldType) in body_fields:
        if fieldType.startswith("UndefOr"):
          fieldType = fieldType.replace("UndefOr", "Option") + " = None"
        print(f"    {fieldName}:{fieldType},")
      for (fieldName, fieldType) in query_fields:
        print(f"    {fieldName}:Option[{scala_type_for_field_type(fieldType)}] = None,")
      if return_type != "Unit":
        print(f"  ):Future[{return_type}] =")
      else:
        print("  ):Unit =")
      print("  {")
      path_format, query_vars = path_format_string(path)
      print("    val url = makeUrl(s\"" + path_format + "\"" + (")" if len(query_vars) == 0 else ","))
      for qvar in query_vars:
        print("                      \"" + qvar + "\" -> " + qvar + ".map { _.toString },")
      if len(query_vars) > 0:
        print("                     )")
      print("    Ajax." + verb.lower() + "(")
      print("      url = url,")
      if len(body_fields) > 0:
        print("      data = Json.obj(")
        for (fieldName, fieldType) in body_fields:
          print(f"        \"{fieldName}\" -> {fieldName},")
        print("      ).toString")
      if(return_type != "Unit"):
        print("    ).map { xhr => ")
        print("      Json.parse(xhr.responseText)")
        print("          .as[" + return_type + "]")
        print("    }")
      else:
        print("    )")

      print("  }")
      print("")
  print("}")

sys.stdout = original_stdout
