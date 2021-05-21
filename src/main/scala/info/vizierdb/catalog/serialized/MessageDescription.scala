package info.vizierdb.catalog.serialized

import play.api.libs.json._

case class MessageDescription(
  `type`: String,
  value: JsValue
)
object MessageDescription
{
  implicit val format: Format[MessageDescription] = Json.format
}