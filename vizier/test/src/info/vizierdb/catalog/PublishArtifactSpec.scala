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
package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.VizierAPI
import info.vizierdb.api._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.commands.data.{ UnloadDataset, LoadDataset }
import info.vizierdb.commands.FileArgument

class PublishArtifactSpec extends Specification with BeforeAll
{
  def beforeAll = SharedTestResources.init

  val EXPORT_NAME = "exported-test"
  val IMPORT_NAME = "imported"

  sequential

  "Publish" >> {
    val project = MutableProject("Publish Test-Publish Artifact")

    project.load("test_data/r.csv", "r")
    project.append("data", "unload")(
      UnloadDataset.PARAM_DATASET -> "r",
      UnloadDataset.PARAM_FORMAT -> "publish_local",
      UnloadDataset.PARAM_OPTIONS -> Seq(
        Map(
          UnloadDataset.PARAM_OPTIONS_KEY -> "name",
          UnloadDataset.PARAM_OPTIONS_VALUE -> EXPORT_NAME,
        )
      )
    )
    project.waitUntilReadyAndThrowOnError

    DB.readOnly { implicit s =>
      PublishedArtifact.getOption(EXPORT_NAME) must not beNone
    }

    val description = GetPublishedArtifact(
      artifactName = EXPORT_NAME
    )
    description.id must beEqualTo(project.artifactRef("r").artifactId.get)

  }

  "Retrieve" >> {
    val project = MutableProject("Publish Test-Retrieve Artifact")

    project.append("data", "load")(
      LoadDataset.PARAM_FILE -> 
        FileArgument(
          url = Some(VizierAPI.urls.publishedArtifact(EXPORT_NAME).toString)
        ),
      LoadDataset.PARAM_NAME -> IMPORT_NAME,
      LoadDataset.PARAM_FORMAT -> "publish_local"
    )
    project.waitUntilReadyAndThrowOnError

    project.artifactRefs.map { _.userFacingName } must contain(IMPORT_NAME)
  }
}
