package info.vizierdb.api.handler

import play.api.libs.json._

object FieldType extends Enumeration
{
  type T = Value
  val INT,
      STRING,
      TAIL = Value

  def parserFor(t: T): (String => JsValue) =
  {
    t match {
      case INT    => (x) => JsNumber(x.toLong)
      case STRING => JsString(_)
      case TAIL   => ???
    }
  }
}