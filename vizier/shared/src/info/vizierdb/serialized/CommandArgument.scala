package info.vizierdb.serialized

import info.vizierdb.nativeTypes

case class CommandArgument(id: String, value: nativeTypes.JsValue)
{
  def tuple = id -> value
}
object CommandArgument
{
  def apply(tuple: (String, nativeTypes.JsValue)): CommandArgument =
    CommandArgument(tuple._1, tuple._2)
}


object CommandArgumentList
{
  type T = Seq[CommandArgument]

  implicit def toMap(list: T): Map[String, nativeTypes.JsValue] = 
    list.map { _.tuple }.toMap

  implicit def toPropertyList(map: Map[String, nativeTypes.JsValue]): T =
    map.toSeq.map { CommandArgument(_) }

  def decode(js: nativeTypes.JsValue): T =
  {
    js.as[Seq[Map[String, nativeTypes.JsValue]]]
      .map { j => CommandArgument(j("id").as[String], j("value")) }
  }

  def decodeAsMap(js: nativeTypes.JsValue): Map[String, nativeTypes.JsValue] =
    decode(js)

  def apply(args: (String, nativeTypes.JsValue)*) =
    args.map  { CommandArgument(_) }
}