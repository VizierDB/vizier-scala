package info.vizierdb.viztrails

import org.squeryl.customtypes.CustomTypesMode._
import org.squeryl.customtypes.BinaryField

import play.api.libs.json.{ Json, JsValue, JsObject, Reads }

class JsonField(v: Array[Byte]) extends BinaryField(v)
{
  lazy val asJson: JsValue = Json.parse(v)

  def as[T](implicit reads: Reads[T]) = asJson.as[T]

  override def toString(): String = asJson.toString()
}
object JsFieldImplicits
{
  implicit def jsonFieldToJsValue(f: JsonField) = f.asJson
  implicit def jsValueToJsonField(j: JsValue) = new JsonField(j.toString().getBytes())
}


