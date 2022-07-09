package info.vizierdb.serialized

import info.vizierdb.types._

case class DatasetColumn(
  id: Long,
  name: String,
  `type`: CellDataType,
)
{
  def dataType = `type`
}