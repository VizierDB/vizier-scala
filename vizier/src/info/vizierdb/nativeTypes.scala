package info.vizierdb

import play.api.libs.json.{ JsValue => PlayJsValue }
import org.apache.spark.sql.types.{ StructField, DataType }

object nativeTypes
{
  type CellDataType = DataType
  type JsValue = PlayJsValue


  implicit def datasetColumnToStructField(column: serialized.DatasetColumn): StructField =
    StructField(column.name, column.`type`)
}