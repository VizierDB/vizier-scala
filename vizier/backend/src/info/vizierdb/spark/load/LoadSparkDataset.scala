package info.vizierdb.spark.load

import play.api.libs.json._
import org.apache.spark.sql.types.StructField
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.commands.FileArgument
import info.vizierdb.types._
import info.vizierdb.serializers._
import info.vizierdb.spark._
import info.vizierdb.spark.SparkSchema.fieldFormat
import org.apache.spark.sql.DataFrame
import info.vizierdb.Vizier
import org.apache.spark.sql.types.StructType

case class LoadSparkDataset(
  url: FileArgument,
  format: String,
  schema: Seq[StructField],
  sparkOptions: Map[String, String] = Map.empty,
  projectId: Identifier
) extends DataFrameConstructor
  with LazyLogging
  with DefaultProvenance
{
  override def construct(context: Identifier => DataFrame): DataFrame = 
  {
    Vizier.sparkSession
          .read
          .format(format)
          .schema(StructType(schema))
          .options(sparkOptions)
          .load(url.getPath(projectId, noRelativePaths = true)._1)
  }

  override def dependencies: Set[Identifier] = Set.empty
}

object LoadSparkDataset
  extends DataFrameConstructorCodec
{
  implicit val format: Format[LoadSparkDataset] = Json.format
  def apply(v: JsValue): DataFrameConstructor = v.as[LoadSparkDataset]

  val LEADING_WHITESPACE = raw"^[ \t\n\r]+"
  val INVALID_LEADING_CHARS = raw"^[^a-zA-Z_]+"
  val INVALID_INNER_CHARS = raw"[^a-zA-Z0-9_]+"

  def cleanColumnName(name: String): String =
    name.replaceAll(LEADING_WHITESPACE, "")
        .replaceAll(INVALID_LEADING_CHARS, "_")
        .replaceAll(INVALID_INNER_CHARS, "_")

  def infer(
    url: FileArgument,
    format: String,
    schema: Option[Seq[StructField]],
    sparkOptions: Map[String, String] = Map.empty,
    projectId: Identifier
  ): LoadSparkDataset =
  {
    LoadSparkDataset(
      url, 
      format,
      schema.getOrElse {
        Vizier.sparkSession
              .read
              .format(format)
              .options(sparkOptions)
              .load(url.getPath(projectId, noRelativePaths = true)._1)
              .schema
      },
      sparkOptions,
      projectId
    )
  }
}