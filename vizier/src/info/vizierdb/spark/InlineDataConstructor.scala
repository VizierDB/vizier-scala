package info.vizierdb.spark

import play.api.libs.json._
import scala.collection.JavaConverters
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.{ SparkSession, DataFrame, Row }
import org.apache.spark.sql.types._

import info.vizierdb.spark.SparkSchema.fieldFormat

case class InlineDataConstructor(
  schema: Seq[StructField],
  data: Seq[Seq[JsValue]]
) extends DataFrameConstructor
  with DefaultProvenance
{
  def construct(
    spark: SparkSession, 
    context: (Identifier => DataFrame)
  ): DataFrame =
  {
    val types = schema.map { _.dataType }
    val rows:Seq[Row] = 
      data.map { row => 
        Row.fromSeq(row.zip(types).map { case (field, t) => 
          SparkPrimitive.decode(field, t, castStrings = true)
        })
      }
    return spark.createDataFrame(
      JavaConverters.seqAsJavaList(rows),
      StructType(schema)
    )
  }

  def dependencies: Set[Identifier] = Set.empty

}


object InlineDataConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[InlineDataConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[InlineDataConstructor]
}
