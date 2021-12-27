package info.vizierdb.spark.vizual

import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, SparkSession }
import info.vizierdb.spark.{ DataFrameConstructor, DataFrameConstructorCodec, DefaultProvenance }

case class VizualScriptConstructor(
  script: Seq[VizualCommand],
  input: Option[String]
)
  extends DataFrameConstructor
  with DefaultProvenance
{
  def construct(spark: SparkSession, context: Map[String, () => DataFrame]): DataFrame =
    ExecOnSpark(
      input.map { context(_)() }
           .getOrElse { spark.emptyDataFrame },
      script
    )
}

object VizualScriptConstructor 
  extends DataFrameConstructorCodec
{
  implicit val format: Format[VizualScriptConstructor] = Json.format
  def apply(j: JsValue) = j.as[VizualScriptConstructor]
}