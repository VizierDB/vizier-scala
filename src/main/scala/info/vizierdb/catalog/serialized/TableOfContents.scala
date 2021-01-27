package info.vizierdb.catalog.serialized

import play.api.libs.json._
import info.vizierdb.types._

case class TableOfContentsSection(
  title: String,
  titleLevel: Int,
  linkToIdx: Identifier,
  moduleId: Identifier
)

object TableOfContentsSection
{
  implicit val format: Format[TableOfContentsSection] = Json.format
}
