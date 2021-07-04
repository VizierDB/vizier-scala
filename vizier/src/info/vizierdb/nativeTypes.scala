package info.vizierdb

import play.api.libs.json.{ JsValue => PlayJsValue, JsObject => PlayJsObject }
import org.apache.spark.sql.types.{ StructField, DataType }

object nativeTypes
{
  type CellDataType = DataType
  type JsValue = PlayJsValue
  type JsObject = PlayJsObject

  implicit def datasetColumnToStructField(column: serialized.DatasetColumn): StructField =
    StructField(column.name, column.`type`)
}