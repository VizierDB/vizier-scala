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
package info.vizierdb

import java.io.File
import play.api.libs.json._
import info.vizierdb.types._
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.{ Resource, PathResource }
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.server.HttpConfiguration
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.HandlerCollection
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.server.{ Request => JettyRequest }
import javax.servlet.MultipartConfigElement
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.api.response._
import info.vizierdb.api.handler._
import info.vizierdb.api.servlet.{ VizierAPIServlet, VizierUIServlet }

import org.mimirdb.api.{ Request, Response }
import org.mimirdb.api.request.Query
import java.net.{ URL, URI }
import java.sql.Time
import java.time.LocalDateTime
import info.vizierdb.api._
import java.net.URLConnection
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import info.vizierdb.util.Streams
import scala.io.Source
import java.net.InetSocketAddress
import org.mimirdb.util.ExperimentalOptions

object VizierAPI
{
  var server: Server = null
  lazy val debug: Boolean = Vizier.config.devel()

  val DEFAULT_PORT = 9000
  val NAME = "vizier"
  val BACKEND = "SCALA"
  val SERVICE_NAME = s"MIMIR ($BACKEND)"
  val MAX_UPLOAD_SIZE = 1024*1024*100 // 100MB
  val MAX_FILE_MEMORY = 1024*1024*10  // 10MB
  val MAX_DOWNLOAD_ROW_LIMIT = Query.RESULT_THRESHOLD
  val VERSION="1.0.0"
  val DEFAULT_DISPLAY_ROWS = 20


  var urls: VizierURLs = null
  var started: LocalDateTime = null

  lazy val WEB_UI_URL = getClass().getClassLoader().getResource("ui")

  def init(
    publicURL: String = null, 
    port: Int = DEFAULT_PORT, 
    path: File = Vizier.config.basePath()
  )
  {
    if(server != null){ 
      throw new RuntimeException("Can't have two Vizier servers running in one JVM")
    }
    server = 
      if(VizierAPI.debug){
        new Server(port)
      } else {
        new Server(InetSocketAddress.createUnresolved(
          "0.0.0.0",
          port
        ))
      }

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    server.setHandler(context)
    context.setBaseResource(Resource.newResource(new File(".")))


    {
      // Actual API
      val api = new ServletHolder(VizierAPIServlet)
      api.getRegistration()
         .setMultipartConfig(new MultipartConfigElement(
           /* location          = */ (new File(path, "temp")).toString,
           /* maxFileSize       = */ MAX_UPLOAD_SIZE.toLong,
           /* maxRequestSize    = */ MAX_UPLOAD_SIZE.toLong,
           /* fileSizeThreshold = */ MAX_FILE_MEMORY
         ))
      context.addServlet(api, "/vizier-db/api/v1/*")
    }

    {
      val webUI = new ServletHolder("default", VizierUIServlet)
      context.addServlet(webUI, "/*")
    }

    val actualPublicURL =
      Option(publicURL)
        .orElse { Vizier.config.publicURL.get }
        .getOrElse { s"http://localhost:$port/" }

    urls = new VizierURLs(
      ui = new URL(actualPublicURL),
      base = new URL(s"${actualPublicURL}vizier-db/api/v1/"),
      api = None
    )
    server.start()
    // server.dump(System.err)
    started = LocalDateTime.now()
  }
}
