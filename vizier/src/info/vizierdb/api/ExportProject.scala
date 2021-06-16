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
import java.io.File
import info.vizierdb.types._
import info.vizierdb.export.{ ExportProject => DoExport }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.util.Streams
import info.vizierdb.catalog.Project
import java.io.FileOutputStream
import info.vizierdb.api.response.FileResponse
import info.vizierdb.api.handler._

object ExportProject
  extends SimpleHandler
{
  def handle(pathParameters: Map[String, JsValue]): Response =
  {
    val projectId = pathParameters("projectId").as[Long]
    val projectName = 
      DB.readOnly { implicit s => 
        Project.get(projectId)
               .name
      }

    val tempFile = File.createTempFile(s"project_$projectId", ".export")

    Streams.closeAfter(new FileOutputStream(tempFile)) { f => 
      DoExport(projectId = projectId, output = f)
    }

    FileResponse(
      file = tempFile, 
      name = projectName+".vizier", 
      contentType = "application/octet-stream",
      afterCompletedTrigger = { () => tempFile.delete() }
    )
  }
}

