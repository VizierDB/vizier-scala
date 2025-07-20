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

case class CommandArgument(id: String, value: nativeTypes.JsValue)
{
  def tuple = id -> value
}
object CommandArgument
{
  def apply(tuple: (String, nativeTypes.JsValue)): CommandArgument =
    CommandArgument(tuple._1, tuple._2)
}


object CommandArgumentList
{
  type T = Seq[CommandArgument]

  implicit def toMap(list: T): Map[String, nativeTypes.JsValue] = 
    list.map { _.tuple }.toMap

  implicit def toPropertyList(map: Map[String, nativeTypes.JsValue]): T =
    map.toSeq.map { CommandArgument(_) }

  def decode(js: nativeTypes.JsValue): T =
  {
    js.as[Seq[Map[String, nativeTypes.JsValue]]]
      .map { j => CommandArgument(j("id").as[String], j("value")) }
  }

  def decodeAsMap(js: nativeTypes.JsValue): Map[String, nativeTypes.JsValue] =
    decode(js)

  def apply(args: (String, nativeTypes.JsValue)*) =
    args.map  { CommandArgument(_) }
}