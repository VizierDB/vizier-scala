package info.vizierdb.export

import play.api.libs.json._

case class ExportedCommand(
  id: Option[String],
  packageId: String,
  commandId: String,
  arguments: JsValue,
  revisionOfId: Option[String],
  properties: Option[Map[String,JsValue]]
)
{
  lazy val (sanitizedPackageId, sanitizedCommandId) = 
    (packageId, commandId) match {
      case ("python", "code") => ("script", "python")
      case x => x
    }

}


object ExportedCommand
{
  implicit val format: Format[ExportedCommand] = Json.format
}