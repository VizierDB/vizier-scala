package info.vizierdb

import play.api.libs.json.{ JsValue => PlayJsValue, JsObject => PlayJsObject }

object nativeTypes
{
  type JsValue = PlayJsValue
  type JsObject = PlayJsObject
  type CellDataType = JsValue

  case class Caveat(
    message: String,
    family: Option[String],
    key: Seq[JsValue]
  )

  def nativeFromJson(value: JsValue, dataType: CellDataType): Any = value
  def jsonFromNative(value: Any, dataType: CellDataType) = value.asInstanceOf[JsValue]
}