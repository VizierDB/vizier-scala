import re

INPUT_FILE = "src/main/resources/vizier-routes.txt"
OUTPUT_FILE = "src/main/scala/info/vizierdb/api/servlet/VizierAPIServletRoutes.scala"

with open(INPUT_FILE) as f:
  routes = [
    (idx, path, verb, handler)
    for idx, line in enumerate(f.readlines())
    for path, verb, group, handler in [re.split(" +", line.rstrip())]
  ]

matcherDefinitions = []
verbHandlers = {}
preflightPaths = {}

for idx, path, verb, handler in routes:
  pathComponents = path.split("/")
  regexp = []
  fields = []
  for component in pathComponents:
    match = re.match("\\{([^}:]+):([^}]+)\\}", component)
    if match is None:
      regexp += [component]
    else:
      fieldName = match.group(1)
      fieldType = match.group(2)
      if fieldType == "int":
        regexp += ["([0-9]+)"]
        fields += [ (fieldName, "JsNumber({}.toLong)".format(fieldName)) ]
      elif fieldType == "subpath":
        regexp += ["(.+)"]
        fields += [ (fieldName, "JsString({})".format(fieldName)) ]
      else:
        raise ValueError("Unsupported field type: {} in {}".format(
                          fieldType, "/".join(pathComponents)))

  if len(fields) == 0:
    matcher = "\"" + "/".join(pathComponents) + "\""
  else:
    matcher = "ROUTE_PATTERN_{}({})".format(
                idx,
                ", ".join(fieldName for fieldName, fieldEncoder in fields)
              )
    matcherDefinitions += [
      "val ROUTE_PATTERN_{} = \"{}\".r".format(
        idx,
        "/".join(regexp)
      )
    ]
  
  fieldConstructor = "Map({})".format(
      ", ".join(
        "\"{}\" -> {}".format(fieldName, fieldEncoder)
        for fieldName, fieldEncoder in fields
      )
    )

  handlerParameters = ", ".join([fieldConstructor, "connection"])

  invokeHandler = "case {} => {}.handle({})".format(
                    matcher, handler, handlerParameters)

  if verb not in verbHandlers:
    verbHandlers[verb] = []
  verbHandlers[verb] += [invokeHandler]

  if path not in preflightPaths:
    preflightPaths[path] = [[], matcher]
  preflightPaths[path][0] = preflightPaths[path][0] + [verb]

import sys
with open(OUTPUT_FILE, "w") as f:
  sys.stdout = f
  print("package info.vizierdb.api.servlet")
  print("")
  print("/* this file is AUTOGENERATED by `scripts/build_routes` from `src/main/resources/vizier-routes.txt` */")
  print("/* DO NOT EDIT THIS FILE DIRECTLY */")
  print("")
  print("import play.api.libs.json._")
  print("import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}")
  print("import org.mimirdb.api.Response")
  print("import info.vizierdb.types._")
  print("import info.vizierdb.api._")
  print("import info.vizierdb.api.response.CORSPreflightResponse")
  print("import info.vizierdb.api.handler._")
  print("")
  print("trait VizierAPIServletRoutes extends HttpServlet {")
  print("")
  print("  def processResponse(request: HttpServletRequest, output: HttpServletResponse)(response: => Response): Unit")
  print("  def fourOhFour(response: HttpServletRequest): Response")
  print("")

  print("\n".join("  "+matcher for matcher in matcherDefinitions))

  print("")
  for verb in verbHandlers:
    capsedVerb = verb[0].upper()+verb[1:].lower()
    print("  override def do{}(request: HttpServletRequest, response: HttpServletResponse) = ".format(capsedVerb))
    print("  {")
    print("    val connection = new JettyClientConnection(request, response)")
    print("    processResponse(request, response) {")
    print("      request.getPathInfo match {")
    for handler in verbHandlers[verb]:
      print("        "+handler)
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
            ", ".join("\""+verb+"\"" for verb in verbs)
          ))
  print("        case _ => fourOhFour(request)")
  print("      }")
  print("    }")
  print("  }")
  print("}")
