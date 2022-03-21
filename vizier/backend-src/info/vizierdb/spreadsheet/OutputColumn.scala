package info.vizierdb.spreadsheet

import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.DataType
import play.api.libs.json._
import info.vizierdb.spark.SparkSchema._
import info.vizierdb.serializers._

class OutputColumn(val source: ColumnSource, var output: StructField, val id: Long, var position: Int)
{
  def rename(name: String) = 
  {
    output = output.copy(name = name)
  }

  def ref = ColumnRef(id, output.name)
}
object OutputColumn
{
  def apply(source: ColumnSource, output: StructField, id: Long, position: Int): OutputColumn = {
    return new OutputColumn(source, output, id, position)
  }
  def unapply(cS: OutputColumn): Option[(ColumnSource, StructField, Long, Int)] =
  {
    Some((cS.source, cS.output, cS.id, cS.position))
  }

  implicit val outputColumnFormat: Format[OutputColumn] = Json.format
  def mapFrom(idx: Int, field: StructField, id: Long, position: Int) = 
    new OutputColumn(SourceDataset(idx, field), field, id, position)
  def withDefaultValue(field: StructField, defaultValue: Any, id: Long, position: Int) = 
    new OutputColumn(DefaultValue(defaultValue), field, id, position)
}


sealed trait ColumnSource

object ColumnSource {
  implicit val columnSourceFormat: OFormat[ColumnSource] = Json.format[ColumnSource]
}

case class SourceDataset(idx: Int, schema: StructField)
  extends ColumnSource
object SourceDataset {
  implicit val sourceDataSetFormat: Format[SourceDataset] = Json.format
}
case class DefaultValue(defaultValue: Any)
  extends ColumnSource

object DefaultValue{
  def apply(defaultValue: Any): DefaultValue = {
    return new DefaultValue(defaultValue)
  }

  implicit val defaultValueWrites = new Writes[DefaultValue] {
    def writes(dV: DefaultValue): JsValue =
      {
        val t = encodeType(dV.defaultValue.asInstanceOf[StructField].dataType)
        Json.obj(
          "name" -> dV.defaultValue.asInstanceOf[StructField].name,
          "type" -> t,
          "baseType" -> t
        )
      }
  }
  implicit val defaultValueReads = new Reads[DefaultValue]  {
      def reads(j: JsValue): JsResult[DefaultValue] = 
      {
        val fields = j.as[Map[String, JsValue]]
        return JsSuccess(DefaultValue(StructField(
          fields
            .get("name")
            .getOrElse { return JsError("Expected name field") }
            .as[String],
          decodeType(
            fields
              .get("type")
              .getOrElse { return JsError("Expected type field") }
              .as[String]
          ).asInstanceOf[DataType]
        )))
      }
    }
  implicit val defaultValueFormat: Format[DefaultValue] = Format(defaultValueReads, defaultValueWrites)
}
