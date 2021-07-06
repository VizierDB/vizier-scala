package info.vizierdb.serialized

import info.vizierdb.nativeTypes.DateTime

case class Timestamps(
  createdAt: DateTime,
  startedAt: Option[DateTime],
  finishedAt: Option[DateTime]
)

