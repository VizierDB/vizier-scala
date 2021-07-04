package info.vizierdb

import play.api.libs.json._
import info.vizierdb.api.AppendModule
import org.mimirdb.spark.{ SparkSchema, SparkPrimitive }

object serializers
{

  implicit val propertyFormat: Format[serialized.Property] = Json.format
  implicit val propertyListFormat: Format[serialized.PropertyList.T] = Json.format

  implicit val branchSourceFormat: Format[serialized.BranchSource] = Json.format
  implicit val commandDescriptionFormat: Format[serialized.CommandDescription] = Json.format
  implicit val moduleOutputDescriptionFormat: Format[serialized.ModuleOutputDescription] = Json.format
  implicit val moduleDescriptionFormat: Format[serialized.ModuleDescription] = Json.format
  implicit val messageDescriptionFormat: Format[serialized.MessageDescription] = Json.format
  implicit val tableOfContentsEntryFormat: Format[serialized.TableOfContentsEntry] = Json.format

  implicit val timestampsFormat: Format[serialized.Timestamps] = Json.format

  implicit val datasetColumnFormat: Format[serialized.DatasetColumn] = Json.format
  implicit val datasetRowFormat: Format[serialized.DatasetRow] = Json.format
  implicit val datasetAnnotationFormat: Format[serialized.DatasetAnnotation] = Json.format

  implicit val workflowSummaryFormat: Format[serialized.WorkflowSummary] = Json.format
  implicit val workflowDescriptionFormat: Format[serialized.WorkflowDescription] = Json.format

  implicit val parameterArtifactFormat: Format[serialized.ParameterArtifact] = Format[serialized.ParameterArtifact](
    new Reads[serialized.ParameterArtifact]{
      def reads(j: JsValue): JsResult[serialized.ParameterArtifact] =
      {
        val dataType = SparkSchema.decodeType( (j \ "dataType").as[String] )
        val value = SparkPrimitive.decode( (j \ "value").get, dataType)
        JsSuccess(serialized.ParameterArtifact(value, dataType))
      }
    },
    new Writes[serialized.ParameterArtifact]{
      def writes(p: serialized.ParameterArtifact): JsValue =
        Json.obj(
          "value" -> p.jsonValue,
          "dataType" -> p.jsonDataType
        )
    }
  )

}