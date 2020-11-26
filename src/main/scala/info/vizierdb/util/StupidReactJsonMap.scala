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

  def apply(saneMap: Map[String, JsValue], extras: (String, JsValue)*): T =
    (saneMap ++ extras.toMap).toSeq.map { case (k, v) => 
      StupidReactJsonField(k, v)
    }

  def apply(elements: (String, JsValue)*): T =
    elements.map { case (k, v) => 
      StupidReactJsonField(k, v)
    }

  def decode(dumbMap: T): Map[String, JsValue] =
    dumbMap.map { field => 
      field.key -> field.value
    }.toMap

  def decode(json: JsValue): Map[String, JsValue] =
    decode(json.as[T])

}