package info.vizierdb.serialized

import info.vizierdb.nativeTypes.{ CellDataType, JsValue, nativeFromJson, jsonFromNative }

case class ParameterArtifact(
  value: JsValue,
  dataType: CellDataType
)
{
  def nativeValue = nativeFromJson(value, dataType)
}
object ParameterArtifact
{
  def fromNative(value: Any, dataType: CellDataType) =
    ParameterArtifact(jsonFromNative(value, dataType), dataType)
}