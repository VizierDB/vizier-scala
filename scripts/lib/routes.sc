val PATH_VARIABLE = "\\{(\\w+):(\\w+)\\}".r
import $file.regexp, regexp.{ UNDEFOR }

sealed trait PathComponent

case class PathLiteral(value: String) extends PathComponent
{
  override def toString = value
}
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

  override def toString = s"{${identifier}}"
}

case class Param(identifier: String, scalaType: String)
{
  def typedIdentifier = s"$identifier:$scalaType"

  def nativeScalaType = 
    scalaType match {
      case "FILE" => "Array[Byte]"
      case UNDEFOR(body) => s"Option[$body]"
      case "JSON" => "JsValue"
      case x => x
    }

  def typedNativeIdentifier = s"$identifier:$nativeScalaType"

  def isOptional =
    scalaType.startsWith("UndefOr") || scalaType.startsWith("Option")
}

sealed trait BodyType
{
  def toParams: Seq[Param]
}
case class FileBody(label: String) extends BodyType
{
  def toParam = Param(label, "FILE")
  def toParams = Seq(toParam)
}
case class RawJsonBody(label: String) extends BodyType
{
  def toParam = Param(label, "JSON")
  def toParams = Seq(toParam)
}
case class RawStringBody(label: String) extends BodyType
{
  def toParam = Param(label, "String")
  def toParams = Seq(toParam)
}
case class JsonParamBody(params: Seq[Param]) extends BodyType
{
  def toParams = params
}
case object EmptyBody extends BodyType
{
  def toParams = Seq.empty
}

case class Route(
  path : Seq[PathComponent],
  pathQueryParams: Seq[PathVariable],
  verb : String,
  domain: String,
  action: String,
  handler: String,
  returns: String,
  body: BodyType,
)
{
  def allParams = Seq[Seq[Param]](
    pathParams,
    pathQueryParams.map { _.toParam },
    body.toParams
  ).flatten

  def pathParams = 
    path.collect { case p:PathVariable => (p:PathVariable).toParam }

  def bodyParams =
    body.toParams

  def jsonParamClassName: Option[String] =
    if(body.isInstanceOf[JsonParamBody]){
      Some(s"${action.capitalize}Parameter")
    } else { None }

  def jsonParamClass: Option[String] =
    body match {
      case JsonParamBody(params) =>
        Some(
          s"""case class ${jsonParamClassName.get}(
             |  ${params.map { _.typedNativeIdentifier }.mkString(",\n  ")}
             |)""".stripMargin
        )
      case _ => None
    }
  
  def actionLabel = s"${domain}${action.split("_").map { _.capitalize }.mkString}"

  def pathString = path.mkString("/")

  def returnsAsScalaType =
    returns match {
      case "STRING" => "String"
      case x => x
    }
}

def readRoutes(path: os.Path): Seq[Route] = 
  os.read(path).split("\n")
    .toSeq
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

      val body =
        jsonParams match {
          case Seq(Param(label, "FILE"))   => FileBody(label)
          case Seq(Param(label, "JSON"))   => RawJsonBody(label)
          case Seq(Param(label, "STRING")) => RawStringBody(label)
          case Seq()                       => EmptyBody
          case params                      => JsonParamBody(params) 
        }

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
        body = body,
      )
    }
