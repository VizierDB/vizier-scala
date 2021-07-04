package info.vizierdb.serialized

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

case class Timestamps(
  createdAt: String,
  startedAt: Option[String],
  finishedAt: Option[String]
)

object Timestamps
{

  def format(t: ZonedDateTime): String =
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(t)

  def apply(
    createdAt: ZonedDateTime,
    startedAt: Option[ZonedDateTime] = None,
    finishedAt: Option[ZonedDateTime] = None,
  ): Timestamps =
    Timestamps(
      createdAt  = format(createdAt),
      startedAt  = startedAt.map { format(_) },
      finishedAt = finishedAt.map { format(_) }
    )
}

