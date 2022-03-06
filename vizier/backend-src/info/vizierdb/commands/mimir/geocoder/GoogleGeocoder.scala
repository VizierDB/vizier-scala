package info.vizierdb.commands.mimir.geocoder

import play.api.libs.json._

class GoogleGeocoder(apiKey: String) extends WebJsonGeocoder(
  JsPath \ "results" \ 0 \ "geometry" \ "location" \ "lat",
  JsPath \ "results" \ 0 \ "geometry" \ "location" \ "lng",
  "GOOGLE",
  "Google Maps"
)
{
  def url(house: String, street: String, city: String, state: String) =
    s"https://maps.googleapis.com/maps/api/geocode/json?address=${s"$house+${street.replaceAll(" ", "+")},+${city.replaceAll(" ", "+")},+$state".replaceAll("\\+\\+", "+")}&key=$apiKey"
}