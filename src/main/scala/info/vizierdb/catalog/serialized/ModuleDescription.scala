package info.vizierdb.catalog.serialized

import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.types._

case class ModuleOutputDescription(
  stderr: Seq[JsValue],
  stdout: Seq[JsValue]
)

object ModuleOutputDescription
{
  implicit val format: Format[ModuleOutputDescription] = Json.format
}

case class ModuleDescription(
  id: Identifier,
  state: Int,
  command: CommandDescription,
  text: String,
  timestamps: Timestamps,
  datasets: Seq[JsObject],
  charts: Seq[JsObject],
  artifacts: Seq[JsObject],
  outputs: ModuleOutputDescription,
  links: HATEOAS.T
)

object ModuleDescription
{
  implicit val format: Format[ModuleDescription] = Json.format
}