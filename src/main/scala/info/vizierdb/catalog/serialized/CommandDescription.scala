package info.vizierdb.catalog.serialized

import play.api.libs.json._

case class CommandDescription(
  packageId: String,
  commandId: String,
  arguments: JsArray
)

object CommandDescription
{
  implicit val format: Format[CommandDescription] = Json.format
}