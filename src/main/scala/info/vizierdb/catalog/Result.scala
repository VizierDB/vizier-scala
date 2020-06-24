package info.vizierdb.catalog

import java.time.ZonedDateTime
import info.vizierdb.types._

case class Result(
  val id: Identifier,
  val started: ZonedDateTime,
  var finished: Option[ZonedDateTime],
)