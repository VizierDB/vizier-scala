import $file.routes, routes._

sealed trait AkkaDirective
{
  def render(prefix: String, outputs: Seq[String]): String
  def push(lead: String, outputs:Seq[String] = Seq.empty): AkkaStack =
    AkkaStack(lead, this, outputs)
}
case class AkkaBody(render: Seq[String] => Seq[String]) extends AkkaDirective
{
  def render(prefix: String, outputs: Seq[String]): String =
    this.render(outputs).map { prefix + _ }.mkString("\n")
}
case class AkkaStack(lead: String, body: AkkaDirective, outputs: Seq[String] = Seq.empty) extends AkkaDirective
{
  def render(prefix: String, outputs: Seq[String]): String =
    prefix + lead + "\n" + 
    body.render(prefix+"  ", outputs ++ this.outputs) + "\n" + 
    prefix + "}"
}
case class AkkaConcat(body: Seq[AkkaDirective]) extends AkkaDirective
{
  def render(prefix: String, outputs: Seq[String]): String =
    prefix + "concat(\n" + 
    body.map { _.render(prefix+"  ", outputs) }.mkString(",\n") + "\n" + 
    prefix + ")"
}



def baseHandler(route: Route): AkkaDirective =
  AkkaBody( outputs => {
    val ret = 
      s"${route.handler}(${outputs.mkString(", ")})"
    route.returns match {
      case "STRING" => Seq(
        "info.vizierdb.api.response.StringResponse(",
        "  " + ret + ",",
        "  contentType = \"text/plain\"",
        ")"
      )
      case _ => Seq(ret)
    }
  })

def decodeBody(route: Route, handler: AkkaDirective): AkkaDirective =
  route.body match {
    case EmptyBody => handler
    case JsonParamBody(params) =>
      handler.push(
        s"entity(as[${route.jsonParamClassName.get}]) { jsonEntity => ",
        outputs = 
          params.map { param =>
            s"${param.identifier} = jsonEntity.${param.identifier}"
          }
      )
    case FileBody(identifier) =>
      handler.push(
        s"VizierServer.withFile(\"${identifier}\") { fileEntity => ",
        outputs = Seq(s"$identifier = fileEntity")
      )
    case RawJsonBody(identifier) =>
      handler.push(
        s"entity(as[JsValue]) { jsonEntity =>",
        outputs = Seq(s"$identifier = jsonEntity")
      )
    case RawStringBody(identifier) =>
      handler.push(
        s"entity(VizierServer.stringRequestUnmarshaller) { stringEntity =>",
        outputs = Seq(s"$identifier = stringEntity")
      )
  }

def decodePathQuery(route: Route, handler: AkkaDirective): AkkaDirective =
{
  if(route.pathQueryParams.isEmpty){ handler }
  else {
    handler.push(
      "extractRequest { httpRequest => val query = httpRequest.getUri.query.toMap.asScala",
      outputs = 
        route.pathQueryParams.map { param =>
          s"${param.identifier} = query.get(\"${param.identifier}\").map { x => ${param.cast("x")} }"
        }
    )
  }  
}

def decodePath(route: Route, handler: AkkaDirective): AkkaDirective =
{
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

  if(path.isEmpty){ handler }
  else {
    if(pathParamInputs.isEmpty){
      handler.push(
        s"path(${path.mkString(" / ")}) {"
      )
    } else { 
      handler.push(
        s"path(${path.mkString(" / ")}) { (${pathParamInputs.mkString(", ")}) =>",
        outputs = pathParamOutputs
      )
    }
  }
}