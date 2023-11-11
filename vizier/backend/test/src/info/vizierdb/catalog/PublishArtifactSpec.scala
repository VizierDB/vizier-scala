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
import info.vizierdb.api._
import info.vizierdb.test.SharedTestResources
import info.vizierdb.MutableProject
import info.vizierdb.commands.data.{ UnloadDataset, LoadDataset }
import info.vizierdb.commands.FileArgument
import info.vizierdb.Vizier

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
    description.id must beEqualTo(project.artifact("r").id)

  }

  "Retrieve" >> {
    val project = MutableProject("Publish Test-Retrieve Artifact")

    project.append("data", "load")(
      LoadDataset.PARAM_FILE -> 
        FileArgument(
          url = Some(Vizier.urls.publishedArtifact(EXPORT_NAME).toString)
        ),
      LoadDataset.PARAM_NAME -> IMPORT_NAME,
      LoadDataset.PARAM_FORMAT -> "publish_local"
    )
    project.waitUntilReadyAndThrowOnError

    project.artifacts.keys must contain(IMPORT_NAME)
  }

  "testing profiler" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/output_with_nulls.csv",
      name = "test", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("true"),
    )

    val df = project.dataframe("test")
    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties

    properties.get("is_profiled") must beSome.which(_ == JsBoolean(true))
  }

    "testing correct profiler information" >> {
    val project = MutableProject("test_profiler")
    project.load(
      file = "test_data/output_with_nulls.csv",
      name = "test", 
      format = "csv",
    )

    GetArtifact(
      projectId = project.projectId,
      artifactId = project.artifact("test").id,
      profile = Some("true"),
    )

    val df = project.dataframe("test")
    val art = project.artifact("test")
    val properties = art.datasetDescriptor.properties
    val columnsProperty = properties.get("columns")

    columnsProperty match {
      case Some(jsValue: JsValue) =>
        // Parse the JSON string
        val json = Json.parse(jsValue.toString())

        // Validate and extract columns array
        (json \ "columns").validate[JsArray] match {
          case JsSuccess(columnsArray, _) =>
            columnsArray.value.foreach { column =>
              // Extract and test distinctValueCount
              val distinctValueCount = (column \ "distinctValueCount").as[Int]
              distinctValueCount must beEqualTo(0)

              // Extract and test nullCount
              val nullCount = (column \ "nullCount").as[Int]
              nullCount must beEqualTo(25)

              println(s"Column Test Passed: distinctValueCount: $distinctValueCount, nullCount: $nullCount")
            }

          case JsError(errors) =>
            failure(s"Error parsing columns JSON: $errors")
        }

      case _ => 
        failure("Columns property not found or not a valid JSON object")
    }

    ok
  }



}
