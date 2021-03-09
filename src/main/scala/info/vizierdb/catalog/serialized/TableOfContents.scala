package info.vizierdb.catalog.serialized

import play.api.libs.json._
import info.vizierdb.types._

case class TableOfContentsEntry(
  title: String,
  titleLevel: Option[Int],
  linkToIdx: Identifier,
  moduleId: Identifier
)

object TableOfContentsEntry
{
  implicit val format: Format[TableOfContentsEntry] = Json.format
}
