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

import java.io.File
import scalikejdbc.DB
import play.api.libs.json._
import java.io.InputStream
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Project, Artifact }
import org.mimirdb.api.{ Request, Response }
import info.vizierdb.types._
import info.vizierdb.artifacts.{ DatasetColumn, DatasetRow, DatasetAnnotation }
import org.mimirdb.api.request.LoadInlineRequest
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import info.vizierdb.filestore.Filestore
import java.io.FileOutputStream
import info.vizierdb.util.Streams
import org.eclipse.jetty.server.{ Request => JettyRequest }
import info.vizierdb.api.response._
import info.vizierdb.api.handler.Handler

object CreateFileHandler
  extends Handler
{
  def handle(
    pathParameters: Map[String, JsValue], 
    request: HttpServletRequest 
  ): Response =
  {
    val projectId = pathParameters("projectId").as[Long]
    val jettyRequest = request.asInstanceOf[JettyRequest]
    val part = jettyRequest.getPart("file")
    if(part == null){
      throw new IllegalArgumentException("No File Provided")
    }
    val content = part.getInputStream()
    handle(projectId, content)
  }
  def handle(
    projectId: Identifier,
    content: InputStream
  ): Response = {
    DB.autoCommit { implicit s => 
      val project = 
        Project.lookup(projectId)
               .getOrElse { 
                  return NoSuchEntityResponse()
               }

      val artifact = Artifact.make(
        project.id, 
        ArtifactType.FILE,
        "application/octet-stream",
        Array[Byte]()
      )

      val file: File = Filestore.get(project.id, artifact.id)

      Streams.cat(content, new FileOutputStream(file))

      RawJsonResponse(
        artifact.summarize()
      )
    }
  } 

}
