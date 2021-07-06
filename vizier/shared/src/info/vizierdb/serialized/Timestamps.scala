package info.vizierdb.serialized

import info.vizierdb.nativeTypes.DateTime

case class Timestamps(
  createdAt: DateTime,
  startedAt: Option[DateTime],
  finishedAt: Option[DateTime]
)

object Timestamps
{
  def apply(createdAt: DateTime, startedAt: DateTime = null, finishedAt: DateTime = null): Timestamps =
    Timestamps(
      createdAt = createdAt,
      startedAt = Option(startedAt),
      finishedAt = Option(finishedAt)
    )
}
