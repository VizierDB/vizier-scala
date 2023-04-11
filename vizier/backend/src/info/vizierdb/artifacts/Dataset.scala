package info.vizierdb.artifacts

import play.api.libs.json._
import info.vizierdb.spark.{
  DataFrameConstructor,
  DataFrameConstructorCodec,
  SparkSchema,
}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.StructField
import info.vizierdb.spark.SparkSchema.fieldFormat

import info.vizierdb.types._
import info.vizierdb.catalog.Artifact

case class Dataset(
  deserializer: String,
  parameters: JsObject,
  properties: Map[String, JsValue]
)
{
  def constructor =
  {
    val deserializerClass = 
      Class.forName(deserializer)
    val deserializerInstance: DataFrameConstructorCodec = 
      deserializerClass
           .getField("MODULE$")
           .get(deserializerClass)
           .asInstanceOf[DataFrameConstructorCodec]
    deserializerInstance(parameters)
  }

  def construct(context: Identifier => Artifact) =
    constructor.construct(context)

  def provenance(context: Identifier => Artifact) =
    constructor.provenance(context)

  def withProperty(prop: (String, JsValue)*): Dataset =
    copy(
      properties = properties ++ prop.toMap
    )

  def schema = 
    constructor.schema

  def transitiveDependencies(
    discovered: Map[Identifier, Artifact], 
    ctx: Identifier => Artifact
  ): Map[Identifier, Artifact] = 
  {
    val newDeps = 
      constructor.dependencies
                 .filterNot { discovered contains _ }
                 .map { x => x -> ctx(x) }
                 .toMap
    newDeps.values
           .foldRight(discovered ++ newDeps) { (dep, accum) =>
              dep match {
                case ds if ds.t == ArtifactType.DATASET => 
                  ds.datasetDescriptor
                    .transitiveDependencies(accum, ctx)
                case _ => accum
              }
            }
  }


}

object Dataset
{
  implicit val format: Format[Dataset] = Format(
    new Reads[Dataset] {
      def reads(j: JsValue): JsResult[Dataset] = 
      {
        JsSuccess(new Dataset(
          deserializer = (j \ "deserializer").asOpt[String]
            .getOrElse { return JsError(Seq(JsPath \ "deserializer" -> Seq())) },
          parameters = (j \ "parameters").asOpt[JsObject]
            .getOrElse { return JsError(Seq(JsPath \ "parameters" -> Seq())) },
          properties = (j \ "properties").asOpt[Map[String, JsValue]]
            .getOrElse { Map.empty },
        ))
      }
    },
    Json.writes[Dataset]
  )

  def apply[T <: DataFrameConstructor](
    constructor: T,
    properties: Map[String, JsValue] = Map.empty
  )(implicit writes: Writes[T]): Dataset =
  {
    Dataset(
      constructor.deserializer,
      Json.toJson(constructor).as[JsObject],
      properties
    )
  }

}