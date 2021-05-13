package info.vizierdb.catalog.serialized

import play.api.libs.json._
import org.apache.spark.sql.types.DataType
import org.mimirdb.spark.Schema.dataTypeFormat
import org.mimirdb.spark.{ Schema => SparkSchema }
import org.mimirdb.spark.SparkPrimitive

case class ParameterArtifact(
  value: Any,
  dataType: DataType
)
{
  def jsonValue = SparkPrimitive.encode( value, dataType )
  def jsonDataType = SparkSchema.encodeType(dataType)
}

object ParameterArtifact
{
  implicit val format: Format[ParameterArtifact] = Format[ParameterArtifact](
    new Reads[ParameterArtifact]{
      def reads(j: JsValue): JsResult[ParameterArtifact] =
      {
        val dataType = SparkSchema.decodeType( (j \ "dataType").as[String] )
        val value = SparkPrimitive.decode( (j \ "value").get, dataType)
        JsSuccess(ParameterArtifact(value, dataType))
      }
    },
    new Writes[ParameterArtifact]{
      def writes(p: ParameterArtifact): JsValue =
        Json.obj(
          "value" -> p.jsonValue,
          "dataType" -> p.jsonDataType
        )
    }
  )
}