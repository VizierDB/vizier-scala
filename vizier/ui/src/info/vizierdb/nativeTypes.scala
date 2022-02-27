package info.vizierdb

import play.api.libs.json.{ JsValue => PlayJsValue, JsObject => PlayJsObject }

object nativeTypes
{
  type JsValue = PlayJsValue
  type JsObject = PlayJsObject
  type CellDataType = JsValue
  type DateTime = scala.scalajs.js.Date
  type URL = String

  case class Caveat(
    message: String,
    family: Option[String],
    key: Seq[JsValue]
  )

  def nativeFromJson(value: JsValue, dataType: CellDataType): Any = value
  def jsonFromNative(value: Any, dataType: CellDataType) = value.asInstanceOf[JsValue]
  def dateDiffMillis(from: DateTime, to: DateTime): Long = { (to.getTime - from.getTime).toLong }
  def formatDate(date: DateTime): String = date.toLocaleDateString() + " " + date.toLocaleTimeString()
}