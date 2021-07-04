package info.vizierdb.serialized

import info.vizierdb.nativeTypes.CellDataType

case class ParameterArtifact(
  value: Any,
  dataType: CellDataType
)