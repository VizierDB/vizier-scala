package info.vizierdb.spreadsheet

import org.apache.spark.sql.types.StructField

class OutputColumn(source: ColumnSource, var output: StructField)
{
  def rename(name: String) = 
  {
    output = output.copy(name = name)
  }
}
object OutputColumn
{
  def mapFrom(idx: Int, field: StructField) = new OutputColumn(SourceDataset(idx, field), field)
  def withDefaultValue(field: StructField, defaultValue: Any) = new OutputColumn(DefaultValue(defaultValue), field)
}


sealed trait ColumnSource

case class SourceDataset(idx: Int, schema: StructField)
  extends ColumnSource
case class DefaultValue(defaultValue: Any)
  extends ColumnSource
