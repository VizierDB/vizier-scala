/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
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