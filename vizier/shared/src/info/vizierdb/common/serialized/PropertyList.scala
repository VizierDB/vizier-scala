package info.vizierdb.common.serialized


case class Property(key: String, value: Any)
{
  def tuple = key -> value
}
object Property
{
  def apply(tuple: (String, Any)): Property =
    Property(tuple._1, tuple._2)
}


object PropertyList
{
  type T = Seq[Property]

  implicit def toMap(list: T): Map[String, Any] = 
    list.map { _.tuple }.toMap

  implicit def toPropertyList(map: Map[String, Any]): T =
    map.toSeq.map { Property(_) }
}