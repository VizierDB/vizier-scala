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