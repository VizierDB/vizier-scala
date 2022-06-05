package info.vizierdb.spark

import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.commands.FileArgument
import org.apache.spark.sql.DataFrame
import play.api.libs.json.JsValue
import org.apache.spark.ml.PipelineModel
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat
import info.vizierdb.catalog.Artifact

case class PipelineModelConstructor(
  input: Identifier,
  url: FileArgument,
  projectId: Identifier,
  schema: Seq[StructField]
) extends DataFrameConstructor 
  with DefaultProvenance
{

  override def dependencies: Set[Identifier] = Set(input)

  def pipeline: PipelineModel = 
    PipelineModel.load(url.getPath(projectId, noRelativePaths = true)._1)

  override def construct(context: Identifier => Artifact): DataFrame = 
    pipeline.transform(context(input).dataframeFromContext(context))


}

object PipelineModelConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[PipelineModelConstructor] = Json.format

  override def apply(j: JsValue): DataFrameConstructor = j.as[PipelineModelConstructor]
}