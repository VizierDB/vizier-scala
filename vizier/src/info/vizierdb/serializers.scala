package info.vizierdb

import play.api.libs.json._
import info.vizierdb.api.AppendModule
import org.mimirdb.spark.{ Schema => SparkSchema, SparkPrimitive }

object serializers
{
  implicit val sparkPrimitiveFormat = SparkSchema.dataTypeFormat

  implicit val simpleParameterDescriptionFormat: Format[serialized.SimpleParameterDescription] = Json.format
  implicit val codeParameterDescriptionFormat: Format[serialized.CodeParameterDescription] = Json.format
  implicit val artifactParameterDescriptionFormat: Format[serialized.ArtifactParameterDescription] = Json.format
  implicit val enumerableValueDescriptionFormat: Format[serialized.EnumerableValueDescription] = Json.format
  implicit val enumerableParameterDescriptionFormat: Format[serialized.EnumerableParameterDescription] = Json.format
  implicit val parameterDescriptionFormat: Format[serialized.ParameterDescription] = Json.format
  implicit val packageCommandFormat: Format[serialized.PackageCommand] = Json.format
  implicit val packageDescriptionFormat: Format[serialized.PackageDescription] = Json.format

  implicit val serviceDescriptorDefaultsFormat: Format[serialized.ServiceDescriptorDefaults] = Json.format
  implicit val serviceDescriptorEnvironmentFormat: Format[serialized.ServiceDescriptorEnvironment] = Json.format
  implicit val serviceDescriptorFormat: Format[serialized.ServiceDescriptor] = Json.format

  implicit val propertyFormat: Format[serialized.Property] = Json.format
  // implicit val propertyListFormat: Format[serialized.PropertyList.T] = Json.format
  implicit val commandArgumentFormat: Format[serialized.CommandArgument] = Json.format
  // implicit val commandArgumentListFormat: Format[serialized.CommandArgumentList.T] = Json.format

  implicit val artifactSummaryFormat = Format[serialized.ArtifactSummary](
    new Reads[serialized.ArtifactSummary]{
      def reads(j: JsValue): JsResult[serialized.ArtifactSummary] =
        if( (j \ "columns").isDefined ) {
          JsSuccess(j.as[serialized.DatasetSummary])
        } else {
          JsSuccess(j.as[serialized.StandardArtifact])
        }
    },
    new Writes[serialized.ArtifactSummary]{
      def writes(v: serialized.ArtifactSummary): JsValue =
        v match {
          case a:serialized.StandardArtifact => Json.toJson(a)
          case a:serialized.DatasetSummary => Json.toJson(a)
          case a:serialized.DatasetDescription => Json.toJson(a)
        }
    }
  )
  implicit val artifactDescriptionFormat = Format[serialized.ArtifactDescription](
    new Reads[serialized.ArtifactDescription]{
      def reads(j: JsValue): JsResult[serialized.ArtifactDescription] =
        if( (j \ "columns").isDefined ) {
          JsSuccess(j.as[serialized.DatasetDescription])
        } else {
          JsSuccess(j.as[serialized.StandardArtifact])
        }
    },
    new Writes[serialized.ArtifactDescription]{
      def writes(v: serialized.ArtifactDescription): JsValue =
        v match {
          case a:serialized.StandardArtifact => Json.toJson(a)
          case a:serialized.DatasetDescription => Json.toJson(a)
        }
    }
  )
  implicit val timestampsFormat: Format[serialized.Timestamps] = Json.format

  implicit val branchSourceFormat: Format[serialized.BranchSource] = Json.format
  implicit val commandDescriptionFormat: Format[serialized.CommandDescription] = Json.format
  implicit val messageTypeFormat = Format[types.MessageType.T](
    new Reads[types.MessageType.T]{
      def reads(j: JsValue) = JsSuccess(types.MessageType.withName(j.as[String]))
    },
    new Writes[types.MessageType.T]{
      def writes(t: types.MessageType.T) = JsString(t.toString)
    }
  )
  implicit val messageDescriptionFormat: Format[serialized.MessageDescription] = Json.format
  implicit val moduleOutputDescriptionFormat: Format[serialized.ModuleOutputDescription] = Json.format
  implicit val moduleDescriptionFormat: Format[serialized.ModuleDescription] = Json.format
  implicit val tableOfContentsEntryFormat: Format[serialized.TableOfContentsEntry] = Json.format


  implicit val datasetColumnFormat: Format[serialized.DatasetColumn] = Json.format
  implicit val datasetRowFormat: Format[serialized.DatasetRow] = Json.format
  implicit val datasetAnnotationFormat: Format[serialized.DatasetAnnotation] = Json.format

  implicit val standardArtifactFormat: Format[serialized.StandardArtifact] = Json.format
  implicit val datasetSummaryFormat: Format[serialized.DatasetSummary] = Json.format
  implicit val datasetDescriptionFormat: Format[serialized.DatasetDescription] = Json.format
  implicit val parameterArtifactFormat: Format[serialized.ParameterArtifact] = Json.format

  implicit val workflowSummaryFormat: Format[serialized.WorkflowSummary] = Json.format
  implicit val workflowDescriptionFormat: Format[serialized.WorkflowDescription] = Json.format

  implicit val branchSummaryFormat: Format[serialized.BranchSummary] = Json.format
  implicit val branchDescriptionFormat: Format[serialized.BranchDescription] = Json.format
  implicit val branchListFormat: Format[serialized.BranchList] = Json.format

  implicit val projectSummaryFormat: Format[serialized.ProjectSummary] = Json.format
  implicit val projectDescriptionFormat: Format[serialized.ProjectDescription] = Json.format
  implicit val projectListFormat: Format[serialized.ProjectList] = Json.format

}