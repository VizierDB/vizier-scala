package info.vizierdb.spreadsheet

import org.apache.spark.sql.types.StructField

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
  def mapFrom(idx: Int, field: StructField, id: Long, position: Int) = 
    new OutputColumn(SourceDataset(idx, field), field, id, position)
  def withDefaultValue(field: StructField, defaultValue: Any, id: Long, position: Int) = 
    new OutputColumn(DefaultValue(defaultValue), field, id, position)
}


sealed trait ColumnSource

case class SourceDataset(idx: Int, schema: StructField)
  extends ColumnSource
case class DefaultValue(defaultValue: Any)
  extends ColumnSource
