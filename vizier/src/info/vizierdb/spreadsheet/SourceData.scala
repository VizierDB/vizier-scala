package info.vizierdb.spreadsheet

import org.apache.spark.sql.types.StructField

sealed trait SourceData
{
  def schema: Array[StructField]
  def size: Long
  def apply(col: Int, row: Long)
}

case class LiteralSourceData(
  schema: Array[StructField],
  data: Array[Array[Any]]
) extends SourceData
{
  def size: Long = data.size
  def apply(col: Int, row: Long) = 
    data(row.toInt)(col)
}