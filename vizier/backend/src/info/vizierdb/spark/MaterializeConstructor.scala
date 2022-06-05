package info.vizierdb.spark

import play.api.libs.json._
import java.io.File
import org.apache.spark.sql.{ SparkSession, DataFrame }
import org.apache.spark.sql.types.{ DataType, StructField }
import info.vizierdb.spark.rowids.AnnotateWithRowIds
import info.vizierdb.spark.caveats.AnnotateImplicitHeuristics
import org.mimirdb.caveats.implicits._
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.types._
import info.vizierdb.filestore.Filestore
import info.vizierdb.Vizier
import info.vizierdb.catalog.Artifact

case class MaterializeConstructor(
  input: Identifier,
  schema: Seq[StructField], 
  artifactId: Identifier,
  projectId: Identifier,
  format: String, 
  options: Map[String,String],
)
  extends DataFrameConstructor
{
  def construct(
    context: Identifier => Artifact
  ): DataFrame = 
  {
    var parser = Vizier.sparkSession.read.format(format)
    for((option, value) <- options){
      parser = parser.option(option, value)
    }
    val materialized = Filestore.getRelative(projectId = projectId, artifactId = artifactId)
    var df = parser.load(materialized.toString)

    // println(absoluteUrl)

    // add a silent projection to "strip out" all of the support metadata.
    df = df.select( schema.map { field => df(field.name) }:_* )
    
    return df
  }

  def provenance(
    context: Identifier => Artifact
  ): DataFrame = 
    context(input)
      .datasetDescriptor
      .constructor
      .provenance(context)

  def dependencies = Set(input)
}

object MaterializeConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[MaterializeConstructor] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[MaterializeConstructor]

  val DEFAULT_FORMAT = "parquet"
}