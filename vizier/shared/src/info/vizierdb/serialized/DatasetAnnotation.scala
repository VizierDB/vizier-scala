package info.vizierdb.serialized

import info.vizierdb.types.RowIdentifier

case class DatasetAnnotation(
  columnId: Int,
  rowId: RowIdentifier,
  key: String,
  value: String
)
