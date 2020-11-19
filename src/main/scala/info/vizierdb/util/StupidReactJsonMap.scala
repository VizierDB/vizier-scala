package info.vizierdb.util

import play.api.libs.json._

case class StupidReactJsonField(
  key: String,
  value: JsValue
)
object StupidReactJsonField {
  implicit val format: Format[StupidReactJsonField] = Json.format
}

object StupidReactJsonMap
{
  type T = Seq[StupidReactJsonField]

  def apply(saneMap: Map[String, JsValue], extras: (String, JsValue)*): JsArray =
    JsArray((saneMap.toSeq ++ extras).map { case (k, v) => 
      Json.obj("key" -> k, "value" -> v)
    })

  def decode(dumbMap: T): Map[String, JsValue] =
    dumbMap.map { field => 
      field.key -> field.value
    }.toMap

}