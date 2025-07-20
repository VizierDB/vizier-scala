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
package info.vizierdb

import play.api.libs.json.{ JsValue => PlayJsValue, JsObject => PlayJsObject, JsNumber => PlayJsNumber }
import org.apache.spark.sql.types.{ StructField, DataType }
import info.vizierdb.spark.{ SparkPrimitive }
import java.time.temporal.ChronoUnit

object nativeTypes
{
  type CellDataType = DataType
  type JsValue = PlayJsValue
  type JsObject = PlayJsObject
  type JsNumber = PlayJsNumber
  type DateTime = java.time.ZonedDateTime
  type URL = java.net.URL

  implicit def datasetColumnToStructField(column: serialized.DatasetColumn): StructField =
    StructField(column.name, column.`type`)

  def nativeFromJson(value: JsValue, dataType: DataType) = 
    SparkPrimitive.decode(value, dataType)

  def jsonFromNative(value: Any, dataType: DataType) = 
    SparkPrimitive.encode(value, dataType)

  def dateDiffMillis(from: DateTime, to: DateTime): Long = 
    from.until(to, ChronoUnit.MILLIS)

  def formatDate(date: DateTime): String =
    s"${date.getMonth().toString()} ${date.getDayOfMonth()}, ${date.getYear()}, ${date.getHour}:${date.getMinute}"

}