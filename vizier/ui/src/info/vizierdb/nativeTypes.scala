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
package info.vizierdb

import play.api.libs.json.{ JsValue => PlayJsValue, JsObject => PlayJsObject, JsNumber => PlayJsNumber }

object nativeTypes
{
  type JsValue = PlayJsValue
  type JsObject = PlayJsObject
  type JsNumber = PlayJsNumber
  type CellDataType = JsValue
  type DateTime = scala.scalajs.js.Date
  type URL = String

  case class Caveat(
    message: String,
    family: Option[String],
    key: Seq[JsValue]
  )

  def nativeFromJson(value: JsValue, dataType: CellDataType): Any = value
  def jsonFromNative(value: Any, dataType: CellDataType) = value.asInstanceOf[JsValue]
  def dateDiffMillis(from: DateTime, to: DateTime): Long = { (to.getTime - from.getTime).toLong }
  def formatDate(date: DateTime): String = date.toLocaleDateString() + " " + date.toLocaleTimeString()
}