package info.vizierdb.serialized

import info.vizierdb.types.Identifier

case class TableOfContentsEntry(
  title: String,
  titleLevel: Option[Int],
  linkToIdx: Identifier,
  moduleId: Identifier
)
