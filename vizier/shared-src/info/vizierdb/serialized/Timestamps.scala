package info.vizierdb.serialized

import info.vizierdb.nativeTypes.{ DateTime, dateDiffMillis, formatDate }
import java.time.temporal.ChronoUnit
import info.vizierdb.util.StringUtils

case class Timestamps(
  createdAt: DateTime,
  startedAt: Option[DateTime],
  finishedAt: Option[DateTime]
)
{
  def runtimeMillis: Option[Long] = 
    finishedAt.flatMap { end =>
      startedAt.map { start =>
        dateDiffMillis(start, end)
      }
    }

  def runtimeString = 
    runtimeMillis.map { StringUtils.formatDuration(_) }
                 .getOrElse { "Unknown" }

  def createdAtString = 
    formatDate(createdAt)

  def startedAtString = 
    startedAt.map { formatDate(_) }.getOrElse { "Unknown" }

  def finishedAtString = 
    finishedAt.map { formatDate(_) }.getOrElse { "Unknown" }
}

object Timestamps
{
  def apply(createdAt: DateTime, startedAt: DateTime = null, finishedAt: DateTime = null): Timestamps =
    Timestamps(
      createdAt = createdAt,
      startedAt = Option(startedAt),
      finishedAt = Option(finishedAt)
    )
}
