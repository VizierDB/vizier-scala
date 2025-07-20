/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
import info.vizierdb.catalog.{ Project, Artifact }
import info.vizierdb.types._
import info.vizierdb.serialized.{ DatasetColumn, DatasetRow, DatasetAnnotation }
import info.vizierdb.serializers._
import info.vizierdb.nativeTypes.datasetColumnToStructField
import info.vizierdb.api.response._
import info.vizierdb.serialized
import info.vizierdb.spark.InlineDataConstructor
import info.vizierdb.artifacts.Dataset
import info.vizierdb.catalog.Artifact
import info.vizierdb.catalog.CatalogDB

object CreateDataset
{

  def apply(
    projectId: Identifier,
    columns: Seq[DatasetColumn],
    rows: Seq[DatasetRow],
    name: Option[String],
    properties: Option[serialized.PropertyList.T],
    annotations: Option[DatasetAnnotation]
  ): serialized.ArtifactSummary =
  {
    CatalogDB.withDB { implicit s => 
      val project = 
        Project.getOption(projectId)
               .getOrElse { ErrorResponse.noSuchEntity }

      val schema = columns.map { datasetColumnToStructField(_) }
      val artifact = Artifact.make(
        project.id, 
        ArtifactType.DATASET,
        MIME.DATASET_VIEW,
        Json.toJson(Dataset(
          InlineDataConstructor(
            schema = schema,
            data = rows.map { _.values }
          ),
        )).toString.getBytes
      )

      artifact.summarize(name.getOrElse { null })
    }
  } 
}
