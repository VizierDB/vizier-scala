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
WEBSOCKET_API_FILE = "vizier/shared/info/vizierdb/api/websocket/GeneratedWebsocketAPI.scala"
WEBSOCKET_IMPL_FILE = "vizier/src/info/vizierdb/api/websocket/WebsocketRequestRoutes.scala"

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
  "subpath": ("(.+)", None, "String"),
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
  
  invokeHandler = "case {} => {}({})".format(
                    matcher, handler, format_arg_constructor(all_fields))

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
  print("import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}")
  print("import java.net.URLDecoder")
  print("import org.mimirdb.api.Response")
  print("import info.vizierdb.types._")
  print("import info.vizierdb.api._")
  print("import info.vizierdb.api.response.CORSPreflightResponse")
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

WEBSOCKET_IGNORE_ARGS = set(["projectId", "branchId"])
with open(WEBSOCKET_API_FILE, "w") as f:
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
  print("import org.mimirdb.api.request.DataContainer")
  print("")
  print("trait GeneratedWebsocketAPI")
  print("{")

  for idx, path, verb, handler, group, action, return_type, body_fields in routes:
    if group != "workflow" or not (action.startswith("head_")):
      continue
    
    # prefix with group and convert to camelCase
    action = group + "".join([
      word.capitalize()
      for word in action.replace("head_", "").split("_")
    ])

    path_fields_with_scala, regexp, query_fields = extract_path_fields(path)

    fields = [
      fieldName + ":" + fieldTypeBase
      for (fieldName, fieldType, fieldTypeBase) in path_fields_with_scala
    ] + [
      fieldName + ":" + scala_type_for_field_type(fieldType)
      for (fieldName, fieldType) in query_fields
    ] + [
      fieldName + ":" + fieldType.replace("FILE", "(Array[Byte], String)")
      for (fieldName, fieldType) in body_fields
    ]

    fieldString = ", ".join(fields)

    print(f"  def {action}({fieldString}): {return_type}")

  print("}")
sys.stdout = original_stdout
