package info.vizierdb.commands.mimir.geocoder

import scala.util.Random
import play.api.libs.json._

class TestCaseGeocoder(name: String) 
  extends Geocoder(name, s"No-op $name Geocoder")
{
  def apply(house: String, street: String, city: String, state: String): Seq[Double] =
  {
    val rnd = new Random(Seq(house, street, city, state).mkString("; ").hashCode)

    val target = rnd.nextDouble

    if(target < 0.7){
      Seq(
        rnd.nextDouble * 180 - 90, 
        rnd.nextDouble * 360 - 180
      )
    } else if(target < 0.9){
      Seq(
        rnd.nextDouble * 180 - 90, 
        rnd.nextDouble * 360 - 180,
        rnd.nextDouble * 180 - 90, 
        rnd.nextDouble * 360 - 180
      )
    } else { Seq() }

  }
}

object TestCaseGeocoder extends TestCaseGeocoder("TEST")
{
  def apply(name: String) = new TestCaseGeocoder(name)
}