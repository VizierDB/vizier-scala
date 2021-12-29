package info.vizierdb.spark.vizual

import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, SparkSession }
import info.vizierdb.spark.{ DataFrameConstructor, DataFrameConstructorCodec, DefaultProvenance }
import info.vizierdb.types._
import info.vizierdb.Vizier

case class VizualScriptConstructor(
  script: Seq[VizualCommand],
  input: Option[Identifier]
)
  extends DataFrameConstructor
  with DefaultProvenance
{
  def construct(context: Identifier => DataFrame): DataFrame =
    ExecOnSpark(
      input.map { context(_) }
           .getOrElse { Vizier.sparkSession.emptyDataFrame },
      script
    )

  def dependencies = input.toSet
}

object VizualScriptConstructor 
  extends DataFrameConstructorCodec
{
  implicit val format: Format[VizualScriptConstructor] = Json.format
  def apply(j: JsValue) = j.as[VizualScriptConstructor]
}