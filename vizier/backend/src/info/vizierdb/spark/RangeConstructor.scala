package info.vizierdb.spark

import play.api.libs.json._
import info.vizierdb.types._
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.DataFrame
import info.vizierdb.catalog.Artifact
import info.vizierdb.Vizier

case class RangeConstructor(
	start: Long = 0l, 
	end: Long, 
	step: Long = 1l
)
	extends DataFrameConstructor
	with DefaultProvenance
{
	def dependencies = Set[Identifier]()
	def schema: Seq[StructField] = Seq( StructField("id", LongType) )
	def construct(context: Identifier => Artifact): DataFrame = 
		Vizier.sparkSession.range(start, end, step).toDF
}

object RangeConstructor
	extends DataFrameConstructorCodec
{
  implicit val format: Format[RangeConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[RangeConstructor]
}
