package info.vizierdb.commands.mimir

import play.api.libs.json._
import info.vizierdb.commands._
import org.apache.spark.sql.types.StructField
import org.mimirdb.lenses.implementation.GeocoderConfig
import org.mimirdb.lenses.Lenses

object Geocode
  extends LensCommand
{ 
  def lens = Lenses.geocode
  def name: String = "Geocode"

  val PARA_HOUSE_NUMBER       = "strnumber"
  val PARA_STREET             = "strname"
  val PARA_CITY               = "city"
  val PARA_STATE              = "state"
  val PARA_GEOCODER           = "geocoder"
  val PARA_CACHE_CODE         = "cacheCode"

  def lensParameters: Seq[Parameter] = Seq(
    ColIdParameter(id = PARA_HOUSE_NUMBER, name = "House Nr."),
    ColIdParameter(id = PARA_STREET, name = "Street"),
    ColIdParameter(id = PARA_CITY, name = "City"),
    ColIdParameter(id = PARA_STATE, name = "State"),
    EnumerableParameter(id = PARA_GEOCODER, name = "Geocoder", values = EnumerableValue.withNames(
        "Google Maps" -> "GOOGLE",
        "Open Street Maps" -> "OSM"
      ),
      default = Some(1)
    ),
    StringParameter(id = PARA_CACHE_CODE, name = "Cache Code", required = false, hidden = true)
  )
  def lensFormat(arguments: Arguments): String = 
    s"GEOCODE WITH ${arguments.get[String](PARA_GEOCODER)}"

  def lensConfig(arguments: Arguments, schema: Seq[StructField], dataset: String, context: ExecutionContext): JsValue =
  {
    Json.toJson(
      GeocoderConfig(
        houseColumn  = schema(arguments.get[Int](PARA_HOUSE_NUMBER)).name,
        streetColumn = schema(arguments.get[Int](PARA_STREET)).name,
        cityColumn   = schema(arguments.get[Int](PARA_CITY)).name,
        stateColumn  = schema(arguments.get[Int](PARA_STATE)).name,
        geocoder     = Some(arguments.get[String](PARA_GEOCODER)),
        latitudeColumn = None,
        longitudeColumn = None,
        cacheCode = arguments.getOpt[String](PARA_CACHE_CODE)
      )
    )
  }
  def updateConfig(lensArgs: JsValue, schema: Seq[StructField], dataset: String): Map[String,JsValue] = 
    lensArgs.as[GeocoderConfig]
            .cacheCode
            .map { code => PARA_CACHE_CODE -> JsString(code) }
            .toMap
}