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
import info.vizierdb.VizierServlet
import java.io.FileInputStream
import info.vizierdb.export.{ ImportProject => DoImport }
import info.vizierdb.util.Streams.closeAfter

case class ImportProject(
  content: InputStream
)
  extends Request
{
  def handle: Response = 
  {
    val f = File.createTempFile("vizier-", "-import.tgz")
    Streams.closeAfter(new FileOutputStream(f)) {
      Streams.cat(content, _)
    }

    val projectId = 
      { 
        val in = 
          new FileInputStream(f)
        val pid = DoImport(
          in, 
          execute = true, 
          blockOnExecution = false
        )
        in.close
        pid
      }

    DB.readOnly { implicit session => 
      Project.lookup(projectId) match {
        case Some(project) => RawJsonResponse(project.describe)
        case None => NoSuchEntityResponse()
      }
    } 

  } 
}

object ImportProject
{
  def apply(request: JettyRequest): ImportProject =
  {
    val part = request.getPart("file")
    if(part == null){
      throw new IllegalArgumentException("No File Provided")
    }
    ImportProject(part.getInputStream())
  }

  /**
   * Create a VizierServlet request handler for a CreateFile
   *
   * It's a little ugly, but we can't parse the request until we know which processor
   * is going to be used.  This abstraction-violating sacrifice exists mainly to keep
   * VizierAPI neat and tidy.
   */

  def handler: ((HttpServletRequest, HttpServletResponse) => Unit) =
  {
    (request: HttpServletRequest, output: HttpServletResponse) => 
      VizierServlet.process(apply(request.asInstanceOf[JettyRequest]))(request, output)
  }

}
