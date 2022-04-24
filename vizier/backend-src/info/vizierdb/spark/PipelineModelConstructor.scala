package info.vizierdb.spark

import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.commands.FileArgument
import org.apache.spark.sql.DataFrame
import play.api.libs.json.JsValue
import org.apache.spark.ml.PipelineModel

case class PipelineModelConstructor(
  input: Identifier,
  url: FileArgument,
  projectId: Identifier
) extends DataFrameConstructor 
  with DefaultProvenance
{

  override def dependencies: Set[Identifier] = Set(input)

  def pipeline: PipelineModel = 
    PipelineModel.load(url.getPath(projectId, noRelativePaths = true)._1)

  override def construct(context: Identifier => DataFrame): DataFrame = 
    pipeline.transform(context(input))


}

object PipelineModelConstructor
  extends DataFrameConstructorCodec
{
  implicit val format: Format[PipelineModelConstructor] = Json.format

  override def apply(j: JsValue): DataFrameConstructor = j.as[PipelineModelConstructor]
}