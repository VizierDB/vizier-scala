/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb

import play.api.libs.json._
import scala.scalajs.js
import info.vizierdb.spark.caveats.DataContainer

object serializers
{

  implicit val zonedDateTimeFormat = Format[js.Date](
    new Reads[js.Date]  { def reads(j: JsValue)  = JsSuccess(new js.Date(j.as[String])) },
    new Writes[js.Date] { def writes(t: js.Date) = JsString(t.toISOString) }
  )

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

  implicit val datasetColumnFormat: Format[serialized.DatasetColumn] = Json.format
  implicit val datasetRowFormat: Format[serialized.DatasetRow] = Json.format
  implicit val datasetAnnotationFormat: Format[serialized.DatasetAnnotation] = Json.format

  implicit val standardArtifactFormat: Format[serialized.StandardArtifact] = Json.format
  implicit val datasetSummaryFormat: Format[serialized.DatasetSummary] = Json.format
  implicit val datasetDescriptionFormat: Format[serialized.DatasetDescription] = Json.format
  implicit val parameterArtifactFormat: Format[serialized.ParameterArtifact] = Json.format

  implicit val parameterArtifactDescriptionFormat: Format[serialized.JsonArtifactDescription] = Json.format

  implicit val artifactSummaryFormat = Format[serialized.ArtifactSummary](
    new Reads[serialized.ArtifactSummary]{
      def reads(j: JsValue): JsResult[serialized.ArtifactSummary] =
        if( (j \ "columns").isDefined ) {
          JsSuccess(j.as[serialized.DatasetSummary])
        } else if( (j \ "payload").isDefined ) {
          JsSuccess(j.as[serialized.JsonArtifactDescription])          
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
          case a:serialized.JsonArtifactDescription => Json.toJson(a)
        }
    }
  )
  implicit val artifactDescriptionFormat = Format[serialized.ArtifactDescription](
    new Reads[serialized.ArtifactDescription]{
      def reads(j: JsValue): JsResult[serialized.ArtifactDescription] =
        if( (j \ "columns").isDefined ) {
          JsSuccess(j.as[serialized.DatasetDescription])
        } else if( (j \ "payload").isDefined ) {
          JsSuccess(j.as[serialized.JsonArtifactDescription])          
        } else {
          JsSuccess(j.as[serialized.StandardArtifact])
        }
    },
    new Writes[serialized.ArtifactDescription]{
      def writes(v: serialized.ArtifactDescription): JsValue =
        v match {
          case a:serialized.StandardArtifact => Json.toJson(a)
          case a:serialized.DatasetDescription => Json.toJson(a)
          case a:serialized.JsonArtifactDescription => Json.toJson(a)
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
  implicit val tableOfContentsEntryFormat: Format[serialized.TableOfContentsEntry] = Json.format
  implicit val moduleDescriptionFormat: Format[serialized.ModuleDescription] = Json.format


  implicit val workflowSummaryFormat: Format[serialized.WorkflowSummary] = Json.format
  implicit val workflowDescriptionFormat: Format[serialized.WorkflowDescription] = Json.format

  implicit val branchSummaryFormat: Format[serialized.BranchSummary] = Json.format
  implicit val branchDescriptionFormat: Format[serialized.BranchDescription] = Json.format
  implicit val branchListFormat: Format[serialized.BranchList] = Json.format

  implicit val projectSummaryFormat: Format[serialized.ProjectSummary] = Json.format
  implicit val projectDescriptionFormat: Format[serialized.ProjectDescription] = Json.format
  implicit val projectListFormat: Format[serialized.ProjectList] = Json.format


  implicit val insertCellFormat: Format[delta.InsertCell] = Json.format
  implicit val updateCellFormat: Format[delta.UpdateCell] = Json.format
  implicit val deleteCellFormat: Format[delta.DeleteCell] = Json.format
  implicit val updateCellStateFormat: Format[delta.UpdateCellState] = Json.format
  implicit val appendCellMessageFormat: Format[delta.AppendCellMessage] = Json.format
  implicit val updateCellArgumentsFormat: Format[delta.UpdateCellArguments] = Json.format
  implicit val deltaOutputArtifactFormat = Format[delta.DeltaOutputArtifact](
    new Reads[delta.DeltaOutputArtifact]{
      def reads(j: JsValue): JsResult[delta.DeltaOutputArtifact] =
        j match {
          case s:JsString => JsSuccess(delta.DeltaOutputArtifact.fromDeletion(s.value))
          case _ => JsSuccess(delta.DeltaOutputArtifact.fromArtifact(j.as[serialized.ArtifactSummary]))
        }
    },
    new Writes[delta.DeltaOutputArtifact]{
      def writes(a: delta.DeltaOutputArtifact): JsValue =
        a.artifact match {
          case Left(deleted) => JsString(deleted)
          case Right(artifact) => Json.toJson(artifact)
        }
    }
  )
  implicit val updateCellDependenciesFormat: Format[delta.UpdateCellDependencies] = Json.format
  implicit val advanceResultIdFormat: Format[delta.AdvanceResultId] = Json.format
  implicit val updateBranchPropertiesFormat: Format[delta.UpdateBranchProperties] = Json.format
  implicit val updateProjectPropertiesFormat: Format[delta.UpdateProjectProperties] = Json.format
  implicit val workflowDeltaFormat: Format[delta.WorkflowDelta] = Format(
    new Reads[delta.WorkflowDelta]() {
      def reads(j: JsValue): JsResult[delta.WorkflowDelta] =
        (j \ delta.WorkflowDelta.OP_TYPE).as[String] match {
          case delta.WorkflowDelta.INSERT_CELL               => JsSuccess(j.as[delta.InsertCell])
          case delta.WorkflowDelta.UPDATE_CELL               => JsSuccess(j.as[delta.UpdateCell])
          case delta.WorkflowDelta.DELETE_CELL               => JsSuccess(j.as[delta.DeleteCell])
          case delta.WorkflowDelta.UPDATE_CELL_STATE         => JsSuccess(j.as[delta.UpdateCellState])
          case delta.WorkflowDelta.APPEND_CELL_MESSAGE       => JsSuccess(j.as[delta.AppendCellMessage])
          case delta.WorkflowDelta.UPDATE_CELL_DEPENDENCIES  => JsSuccess(j.as[delta.UpdateCellDependencies])
          case delta.WorkflowDelta.ADVANCE_RESULT_ID         => JsSuccess(j.as[delta.AdvanceResultId])
          case delta.WorkflowDelta.UPDATE_CELL_ARGUMENTS     => JsSuccess(j.as[delta.UpdateCellArguments])
          case delta.WorkflowDelta.UPDATE_BRANCH_PROPERTIES  => JsSuccess(j.as[delta.UpdateBranchProperties])
          case delta.WorkflowDelta.UPDATE_PROJECT_PROPERTIES => JsSuccess(j.as[delta.UpdateProjectProperties])
          case _ => JsError()
        }
    },
    new Writes[delta.WorkflowDelta]() {
      def writes(d: delta.WorkflowDelta): JsValue =
        d match { 
          case x:delta.InsertCell              => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.INSERT_CELL))
          case x:delta.UpdateCell              => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.UPDATE_CELL))
          case x:delta.DeleteCell              => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.DELETE_CELL))
          case x:delta.UpdateCellState         => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.UPDATE_CELL_STATE))
          case x:delta.AppendCellMessage       => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.APPEND_CELL_MESSAGE))
          case x:delta.UpdateCellDependencies  => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.UPDATE_CELL_DEPENDENCIES))
          case x:delta.AdvanceResultId         => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.ADVANCE_RESULT_ID))
          case x:delta.UpdateCellArguments     => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.UPDATE_CELL_ARGUMENTS))
          case x:delta.UpdateBranchProperties  => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.UPDATE_BRANCH_PROPERTIES))
          case x:delta.UpdateProjectProperties => Json.toJson(x).as[JsObject] + (delta.WorkflowDelta.OP_TYPE -> JsString(delta.WorkflowDelta.UPDATE_PROJECT_PROPERTIES))
        }
    }
  )
  implicit val caveatFormat = Json.format[nativeTypes.Caveat]
  implicit val dataContainerFormat = Json.format[DataContainer]

  implicit val filesystemObjectFormat = Json.format[serialized.FilesystemObject]

  def playToNativeJson(j: JsValue): js.Any = 
    j match {
      case JsObject(o) => js.Dictionary(o.mapValues { playToNativeJson(_) }.toSeq:_*)
      case JsArray(a) => js.Array(a.map { playToNativeJson(_) }.toSeq:_*)
      case JsString(s) => s
      case JsNumber(n) => n.toDouble
      case JsBoolean(b) => b
      case JsNull => null
    }
  implicit val mlvectorFormat: Format[serialized.MLVector] = Json.format

  implicit val pythonPackageFormat: Format[serialized.PythonPackage] = Json.format
  implicit val pythonEnvironmentDescriptorFormat: Format[serialized.PythonEnvironmentDescriptor] = Json.format
  implicit val pythonEnvironmentSummaryFormat: Format[serialized.PythonEnvironmentSummary] = Json.format
  implicit val pythonSettingsSummaryFormat: Format[serialized.PythonSettingsSummary] = Json.format
}