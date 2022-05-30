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

  def construct(context: Identifier => DataFrame) =
    constructor.construct(context)

  def provenance(context: Identifier => DataFrame) =
    constructor.provenance(context)

  def withProperty(prop: (String, JsValue)*): Dataset =
    copy(
      properties = properties ++ prop.toMap
    )

  def schema = 
    constructor.schema
}

object Dataset
{
  implicit val format: Format[Dataset] = Json.format

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