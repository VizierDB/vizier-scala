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

import java.io.File
import scalikejdbc.DB
import play.api.libs.json._
import java.io.InputStream
import info.vizierdb.VizierAPI
import info.vizierdb.catalog.{ Project, Artifact }
import info.vizierdb.types._
import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import java.io.FileOutputStream
import info.vizierdb.util.Streams
import org.eclipse.jetty.server.{ Request => JettyRequest }
import info.vizierdb.api.response._
import java.io.FileInputStream
import info.vizierdb.export.{ ImportProject => DoImport }
import info.vizierdb.util.Streams.closeAfter
import info.vizierdb.api.handler._
import info.vizierdb.api.handler.ClientConnection
import info.vizierdb.serialized

object ImportProject
{
  // override def filePart = Some("file")
  def apply(
    file: (InputStream, String), 
  ): serialized.ProjectDescription =
  {
    val (content, _) = file

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
      Project.getOption(projectId) match {
        case Some(project) => project.describe
        case None => ErrorResponse.noSuchEntity
      }
    } 

  }  
}

