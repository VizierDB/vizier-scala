package info.vizierdb.spark

import play.api.libs.json._
import java.io.File
import org.apache.spark.sql.{ SparkSession, DataFrame }
import org.apache.spark.sql.types.{ DataType, StructField }
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.spark.caveats.AnnotateImplicitHeuristics
import org.mimirdb.caveats.implicits._
import info.vizierdb.spark.Schema.fieldFormat
import info.vizierdb.types._

case class MaterializeConstructor(
  input: Identifier,
  schema: Seq[StructField], 
  url: String,
  format: String, 
  options: Map[String,String],
  urlIsRelative: Option[Boolean]
)
  extends DataFrameConstructor
{

  def absoluteUrl: String = 
    if(urlIsRelative.getOrElse(false)) {
      MimirAPI.conf.resolveToDataDir(url).toString
    } else { url }

  def construct(
    spark: SparkSession, 
    context: Map[String,() => DataFrame]
  ): DataFrame = 
  {
    var parser = spark.read.format(format)
    for((option, value) <- options){
      parser = parser.option(option, value)
    }
    var df = parser.load(absoluteUrl)

    // println(absoluteUrl)

    // add a silent projection to "strip out" all of the support metadata.
    df = df.select( schema.map { field => df(field.name) }:_* )
    
    return df
  }

  def provenance(
    spark: SparkSession, 
    context: Map[String,() => DataFrame]
  ): DataFrame = 
    context(input)()
}

object MaterializeConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[MaterializeConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[MaterializeConstructor]

  val DEFAULT_FORMAT = "parquet"
}