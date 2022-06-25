package info.vizierdb.spark

import org.apache.spark.sql.types.UserDefinedType
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT
import org.apache.spark.sql.sedona_sql.expressions.ST_AsText
import org.apache.spark.sql.Column

/**
 * Certain column types (most notably UDTs) are not safe for export through 
 * standard mechanisms...  This class contains operations that safely transform
 * the dataframe's attributes into export-friendly types.
 */
object SafeExport
{
  def csv(df: DataFrame): DataFrame =
  {
    val mapping =
      df.schema.fields.map { column =>
        val base = df(column.name)

        column.dataType match {
          case t:UserDefinedType[_] if t.isInstanceOf[GeometryUDT] =>
            new Column(ST_AsText(Seq(base.expr)))

          case _ => 
            base
        }

      }
    
    df.select(mapping:_*)
  }
}