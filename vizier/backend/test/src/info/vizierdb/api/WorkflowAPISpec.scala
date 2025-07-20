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

import java.io.ByteArrayInputStream
import scalikejdbc.DB
import play.api.libs.json._
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import info.vizierdb.test.SharedTestResources
import info.vizierdb.serialized.PropertyList
import info.vizierdb.catalog.Project
import info.vizierdb.types._
import info.vizierdb.api.response.NoSuchEntityResponse
import info.vizierdb.api.response.RawJsonResponse
import java.io.FileInputStream
import info.vizierdb.commands.data.LoadDataset
import info.vizierdb.commands.FileArgument
import info.vizierdb.serialized

class WorkflowAPISpec
  extends Specification
  with BeforeAll
{
  def beforeAll = SharedTestResources.init()

  val PROJECT_NAME = "workflow-api-test"
  val FILE_DATA = """
A,B,C
1,2,3
1,3,1
2,,1
1,2,
1,4,2
2,2,1
4,2,4
"""

  var project = -1l
  var branch = -1l

  sequential

  "Workflow Test Setup" >> {
    val projectSummary: serialized.ProjectSummary = 
      CreateProject(PropertyList(
        "name" -> JsString(PROJECT_NAME)
      ))
    project = projectSummary.id
    branch = projectSummary.defaultBranch

    val descriptor: serialized.ProjectDescription = 
      GetProject(projectId = project)
    ok
  }

  "Create and load a file" >> {
    val fileResponse: serialized.ArtifactSummary = 
      CreateFile(project, (new ByteArrayInputStream(FILE_DATA.getBytes), "test file") )

    var fileId = fileResponse.id

    val appendResponse = AppendModule(
      projectId = project,
      branchId = branch,
      packageId = "data",
      commandId = "load",
      arguments = 
        serialized.CommandArgumentList.toPropertyList(
          LoadDataset.encodeArguments(Map(
            "file" -> FileArgument(filename = Some("R.csv"), fileid = Some(fileId)),
            "name" -> "R",
            "loadFormat" -> "csv",
            "loadDetectHeaders" -> true,
          )).as[Map[String,JsValue]]
        )
    )

    ok
  }
}

