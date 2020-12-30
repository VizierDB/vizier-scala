/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
import info.vizierdb.artifacts.{ DatasetColumn, DatasetRow, DatasetAnnotation }
import org.mimirdb.api.request.LoadInlineRequest
import info.vizierdb.api.response._

case class CreateDataset(
  projectId: Identifier,
  columns: Seq[DatasetColumn],
  rows: Seq[DatasetRow],
  name: Option[String],
  properties: Option[Map[String, JsValue]],
  annotations: Option[DatasetAnnotation]
)
  extends Request
{
  def handle: Response = 
  {
    DB.autoCommit { implicit s => 
      val project = 
        Project.lookup(projectId)
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
        schema = columns.map { _.toSpark },
        data = rows.map { _.values },
        dependencies = None,
        resultName = Some(artifact.nameInBackend),
        properties = properties,
        humanReadableName = name
      ).handle

      RawJsonResponse(
        artifact.summarize(name.getOrElse { null })
      )
    }
  } 
}

object CreateDataset
{
  implicit val format: Format[CreateDataset] = Json.format
}

