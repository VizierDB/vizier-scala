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
package info.vizierdb.serialized

import info.vizierdb.nativeTypes

case class Property(key: String, value: nativeTypes.JsValue)
{
  def tuple = key -> value
}
object Property
{
  def apply(tuple: (String, nativeTypes.JsValue)): Property =
    Property(tuple._1, tuple._2)
}


object PropertyList
{
  type T = Seq[Property]

  implicit def toMap(list: T): Map[String, nativeTypes.JsValue] = 
    list.map { _.tuple }.toMap

  implicit def toPropertyList(map: Map[String, nativeTypes.JsValue]): T =
    map.toSeq.map { Property(_) }

  def apply(properties: (String, nativeTypes.JsValue)*): T =
    properties.map { Property(_) }

  def lookup(properties: T, key: String): Option[nativeTypes.JsValue] =
    properties.find { _.key.equals(key) }
              .map { _.value }
}