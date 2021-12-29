package info.vizierdb.commands.mimir.geocoder

abstract class Geocoder(val name: String, val label: String) extends Serializable {

  def apply(house: String, street: String, city: String, state: String): Seq[Double]

}
