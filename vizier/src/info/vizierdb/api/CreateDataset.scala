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
package info.vizierdb.api

import scalikejdbc.DB
import play.api.libs.json._
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Project, Artifact }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types._
import info.vizierdb.serialized.{ DatasetColumn, DatasetRow, DatasetAnnotation }
import info.vizierdb.serializers._
import info.vizierdb.nativeTypes.datasetColumnToStructField
import org.mimirdb.api.request.LoadInlineRequest
import info.vizierdb.api.response._
import info.vizierdb.serialized.PropertyList

object CreateDataset
{

  def apply(
    projectId: Identifier,
    columns: Seq[DatasetColumn],
    rows: Seq[DatasetRow],
    name: Option[String],
    properties: Option[PropertyList.T],
    annotations: Option[DatasetAnnotation]
  ): Response =
  {
    DB.autoCommit { implicit s => 
      val project = 
        Project.getOption(projectId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }

      val artifact = Artifact.make(
        project.id, 
        ArtifactType.DATASET,
        MIME.DATASET_VIEW,
        Array[Byte]()
      )

      LoadInlineRequest(
        schema = columns.map { datasetColumnToStructField(_) },
        data = rows.map { _.values },
        dependencies = None,
        resultName = Some(artifact.nameInBackend),
        properties = properties.map { PropertyList.toMap(_) },
        humanReadableName = name
      ).handle

      RawJsonResponse(
        artifact.summarize(name.getOrElse { null })
      )
    }
  } 
}
