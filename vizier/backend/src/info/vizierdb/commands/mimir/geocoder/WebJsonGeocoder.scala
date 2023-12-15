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

import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json._
import info.vizierdb.util.HttpUtils

abstract class GeoValue(val value:Double) 

object GeoValue{
  implicit val format: Format[GeoValue] = Format(
  new Reads[GeoValue] {
    def reads(json: JsValue): JsResult[GeoValue] = {
      json match {
        case JsString(s) => JsSuccess(GeoString(s))
        case JsNumber(d) => JsSuccess(GeoDouble(d.toDouble))
        case x => throw new Exception(s"GeoValue: $x not supported")
      }
    }
  }, new Writes[GeoValue] { 
      def writes(data: GeoValue): JsValue = {
        data match {
          case GeoString(s) => JsString(s)
          case GeoDouble(d) => JsNumber(d)
          case x => throw new Exception(s"GeoValue: $x not supported")
        }
      }
  })
}
case class GeoString(s: String) extends GeoValue(s.toDouble)
case class GeoDouble(d: Double) extends GeoValue(d)


abstract class WebJsonGeocoder(
  getLat: JsPath, 
  getLon: JsPath,
  name: String,
  label: String
) 
  extends Geocoder(name, label)
  with LazyLogging
{

  def apply(house: String, street: String, city: String, state: String): Seq[Double]=
  {
    val actualUrl = url(
        Option(house).getOrElse(""), 
        Option(street).getOrElse(""), 
        Option(city).getOrElse(""), 
        Option(state).getOrElse(""))
    try {
      val json = Json.parse(HttpUtils.get(actualUrl))
      val latitude = getLat.read[GeoValue].reads(json).get.value
      val longitude = getLon.read[GeoValue].reads(json).get.value
      return Seq( latitude, longitude )
    } catch {
      case nse: java.util.NoSuchElementException => {
        if(Option(house).isEmpty && Option(street).isEmpty)
          return Seq()
        else
          return apply(null,null,city,state)
      }
      case ioe: Throwable =>  {
        logger.error(s"Exception with Geocoding Request: $actualUrl", ioe)
        Seq()
      }
    }
  }

  def url(house: String, street: String, city: String, state: String): String
}