package info.vizierdb.spark.vizual

import play.api.libs.json._
import org.apache.spark.sql.{ DataFrame, SparkSession }
import info.vizierdb.spark.{ DataFrameConstructor, DataFrameConstructorCodec, DefaultProvenance }
import info.vizierdb.types._
import info.vizierdb.Vizier
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import org.apache.spark.sql.types.StructType

case class VizualScriptConstructor(
  script: Seq[VizualCommand],
  input: Option[Identifier],
  schema: Seq[StructField]
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

  def getSchema(inputSchema: Option[Seq[StructField]], script: Seq[VizualCommand]): Seq[StructField] =
    ExecOnSpark(
      inputSchema.map { sch => 
        Vizier.sparkSession.createDataFrame(new java.util.ArrayList[Row](), StructType(sch)),
      }.getOrElse { Vizier.sparkSession.emptyDataFrame },
      script
    ).schema

}