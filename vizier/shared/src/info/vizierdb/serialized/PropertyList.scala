package info.vizierdb.serialized

import info.vizierdb.nativeTypes

case class Property(key: String, value: nativeTypes.JsValue)
{
  def tuple = key -> value
}
object Property
{
  def apply(tuple: (String, nativeTypes.JsValue)): Property =
    Property(tuple._1, tuple._2)
}


object PropertyList
{
  type T = Seq[Property]

  implicit def toMap(list: T): Map[String, nativeTypes.JsValue] = 
    list.map { _.tuple }.toMap

  implicit def toPropertyList(map: Map[String, nativeTypes.JsValue]): T =
    map.toSeq.map { Property(_) }

  def apply(properties: (String, nativeTypes.JsValue)*): T =
    properties.map { Property(_) }

  def lookup(properties: T, key: String): Option[nativeTypes.JsValue] =
    properties.find { _.key.equals(key) }
              .map { _.value }
}