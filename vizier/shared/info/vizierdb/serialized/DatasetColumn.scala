package info.vizierdb.serialized

import info.vizierdb.types._

case class DatasetColumn(
  id: Int,
  name: String,
  `type`: CellDataType,
)