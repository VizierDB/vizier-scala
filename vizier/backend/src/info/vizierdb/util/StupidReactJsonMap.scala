/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.util

import play.api.libs.json._

case class StupidReactJsonField(
  key: String,
  value: JsValue
)
object StupidReactJsonField {
  implicit val format: Format[StupidReactJsonField] = Json.format
}

object StupidReactJsonMap
{
  type T = Seq[StupidReactJsonField]

  def apply(saneMap: Map[String, JsValue], extras: (String, JsValue)*): T =
    (saneMap ++ extras.toMap).toSeq.map { case (k, v) => 
      StupidReactJsonField(k, v)
    }

  def apply(elements: (String, JsValue)*): T =
    elements.map { case (k, v) => 
      StupidReactJsonField(k, v)
    }

  def encode(saneMap: Map[String, JsValue], extras: (String, JsValue)*): JsArray =
    JsArray(apply(saneMap, extras:_*).map { Json.toJson(_) })
  def encode(elements: (String, JsValue)*): JsArray =
    JsArray(apply(elements:_*).map { Json.toJson(_) })

  def decode(dumbMap: T): Map[String, JsValue] =
    dumbMap.map { field => 
      field.key -> field.value
    }.toMap

  def decode(json: JsValue): Map[String, JsValue] =
    decode(json.as[T])

}

