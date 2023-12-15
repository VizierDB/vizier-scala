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
package info.vizierdb.spreadsheet

import org.apache.spark.sql.types.{ DataType, StructField }
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.spark.SparkPrimitive
import play.api.libs.json._
import info.vizierdb.spark.SparkPrimitive

case class OutputColumn(val source: ColumnSource, var output: StructField, val id: Long, var position: Int)
{
  def rename(name: String) = 
  {
    output = output.copy(name = name)
  }

  def ref = ColumnRef(id, output.name)
}
object OutputColumn
{
  def mapFrom(idx: Int, field: StructField, id: Long, position: Int) = 
    new OutputColumn(SourceDataset(idx, field), field, id, position)
  def withDefaultValue(field: StructField, defaultValue: Any, id: Long, position: Int) = 
    new OutputColumn(DefaultValue(defaultValue), field, id, position)
  
  val FIELD_SOURCE = "source"
  val FIELD_OUTPUT = "output"
  val FIELD_ID = "id"
  val FIELD_POSITION = "position"

  implicit val format = Format[OutputColumn](
    new Reads[OutputColumn] { 
      def reads(json: JsValue): JsResult[OutputColumn] = 
      {
        val output = (json \ FIELD_OUTPUT).as[StructField]
        JsSuccess(OutputColumn(
          source = ColumnSource.fromJson((json \ FIELD_SOURCE).as[JsValue], output.dataType),
          output = output,
          id = (json \ FIELD_ID).as[Long], 
          position = (json \ FIELD_POSITION).as[Int], 
        ))
      }
    },
    new Writes[OutputColumn] {
      def writes(o: OutputColumn): JsValue = 
        Json.obj(
          FIELD_SOURCE -> o.source.toJson(o.output.dataType),
          FIELD_OUTPUT -> o.output,
          FIELD_ID -> o.id,
          FIELD_POSITION -> o.position,
        )
    }
  )
}


sealed trait ColumnSource
{
  def toJson(dataType: DataType): JsObject
}

case class SourceDataset(idx: Int, schema: StructField)
  extends ColumnSource
{
  def toJson(dataType: DataType): JsObject =
    Json.obj(
      ColumnSource.FIELD_TYPE -> ColumnSource.TYPE_DATASET,
      ColumnSource.FIELD_INDEX -> idx,
      ColumnSource.FIELD_SCHEMA -> schema
    )
}
case class DefaultValue(defaultValue: Any)
  extends ColumnSource
{
  def toJson(dataType: DataType): JsObject =
    Json.obj(
      ColumnSource.FIELD_TYPE -> ColumnSource.TYPE_DEFAULT,
      ColumnSource.FIELD_VALUE -> SparkPrimitive.encode(defaultValue, dataType)
    )

}

object ColumnSource
{
  val TYPE_DATASET = "dataset"
  val TYPE_DEFAULT = "default"
  val FIELD_TYPE = "type"
  val FIELD_INDEX = "idx"
  val FIELD_SCHEMA = "schema"
  val FIELD_VALUE = "value"

  def fromJson(data: JsValue, dataType: DataType) = 
  {
    (data \ FIELD_TYPE).as[String] match {
      case TYPE_DATASET => 
        SourceDataset(
          idx = (data \ FIELD_INDEX).as[Int],
          schema = (data \ FIELD_SCHEMA).as[StructField],
        )
      case TYPE_DEFAULT =>
        DefaultValue(
          defaultValue = SparkPrimitive.decode((data \ FIELD_VALUE).get, dataType)
        )
    }
  }
}