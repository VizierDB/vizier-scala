package info.vizierdb

import play.api.libs.json.{ JsValue => PlayJsValue, JsObject => PlayJsObject }
import org.apache.spark.sql.types.{ StructField, DataType }
import info.vizierdb.spark.{ SparkPrimitive }

object nativeTypes
{
  type CellDataType = DataType
  type JsValue = PlayJsValue
  type JsObject = PlayJsObject
  type DateTime = java.time.ZonedDateTime
  type URL = java.net.URL

  implicit def datasetColumnToStructField(column: serialized.DatasetColumn): StructField =
    StructField(column.name, column.`type`)

  def nativeFromJson(value: JsValue, dataType: DataType) = 
    SparkPrimitive.decode(value, dataType)

  def jsonFromNative(value: Any, dataType: DataType) = 
    SparkPrimitive.encode(value, dataType)
}