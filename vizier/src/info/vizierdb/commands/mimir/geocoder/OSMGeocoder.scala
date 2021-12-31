package info.vizierdb.commands.mimir.geocoder

import java.net.URLEncoder
import play.api.libs.json._

class OSMGeocoder(hostURL: String, name: String = "OSM", label: String = "Open Street Maps") extends WebJsonGeocoder(
  JsPath \ 0 \ "lat",
  JsPath \ 0 \ "lon",
  name,
  label
)
{
  def url(house: String, street: String, city: String, state: String) = if(house.isEmpty() && street.isEmpty() && city.isEmpty() && !state.isEmpty()) 
      s"$hostURL/?format=json&q=${URLEncoder.encode(state, "UTF-8")}"
    else
      s"$hostURL/?format=json&street=${URLEncoder.encode(house, "UTF-8")}%20${URLEncoder.encode(street, "UTF-8")}&city=${URLEncoder.encode(city, "UTF-8")}&state=${URLEncoder.encode(state, "UTF-8")}"
    
}