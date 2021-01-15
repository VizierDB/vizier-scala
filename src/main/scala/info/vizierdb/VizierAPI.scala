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

import org.mimirdb.api.{ Request, Response }
import org.mimirdb.api.request.Query
import java.net.{ URL, URI }
import java.sql.Time
import java.time.LocalDateTime
import info.vizierdb.api._
import java.net.URLConnection
import info.vizierdb.util.Streams
import scala.io.Source

object VizierAPI
{
  var server: Server = null
  var debug: Boolean = true

  val DEFAULT_PORT = 5000
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

  def init(port: Int = DEFAULT_PORT, path: File = Vizier.config.basePath())
  {
    if(server != null){ 
      throw new RuntimeException("Can't have two Vizier servers running in one JVM")
    }
    server = new Server(port)

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    server.setHandler(context)
    context.setBaseResource(Resource.newResource(new File(".")))


    {
      // Actual API
      val api = new ServletHolder(VizierServlet)
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

    urls = new VizierURLs(
      ui = new URL(s"http://localhost:$port/"),
      base = new URL(s"http://localhost:$port/vizier-db/api/v1/"),
      api = None
    )
    server.start()
    // server.dump(System.err)
    started = LocalDateTime.now()
  }
}

object VizierUIServlet
  extends HttpServlet
  with LazyLogging
{
  lazy val CLASS_LOADER = getClass().getClassLoader()

  override def doGet(req: HttpServletRequest, output: HttpServletResponse) = 
  {
    try {
      var components = req.getPathInfo.split("/")
      // Strip the leading /
      if(components.headOption.equals(Some(""))){
        components = components.tail
      }
      // Ensure that we have at least one element
      if(components.isEmpty) {
        components = Array("")
      }
      // Redirect meta-URLs to the standard react index page.
      if(components(0).equals("projects")) {
        components = Array("")
      }
      // Redirect empty paths to the relevant index
      if(components.last.equals("")){
        components.update(components.size - 1, "index.html")
      } 
      // Strip out directory cheats
      components = components.filterNot { _.startsWith(".") }
      // Static files are stored in resources/ui
      components = "ui" +: components

      val resourcePath = components.mkString("/")

      logger.debug(s"STATIC GET: $resourcePath")

      val data = getClass()
                    .getClassLoader()
                    .getResourceAsStream(components.mkString("/"))
      if(data == null){
        output.setStatus(HttpServletResponse.SC_NOT_FOUND)
        output.getOutputStream().println("NOT FOUND")
      } else {
        val content = Streams.readAll(data)
        val f = new File(resourcePath)
        val mime = URLConnection.guessContentTypeFromName(f.getName())
        output.setContentType(mime)
        output.setContentLength(content.length)
        output.getOutputStream().write(content)
      }
    } catch {
      case e: Throwable => 
        e.printStackTrace()
    }
  }
}

object VizierServlet
  extends HttpServlet 
  with LazyLogging
{
  val PROJECT  = "\\/projects\\/([0-9]+)(\\/.*|)".r
  val BRANCH   = "\\/branches\\/([0-9]+)(\\/.*|)".r
  val HEAD = "\\/head(\\/.*|)".r
  val WORKFLOW = "\\/workflows\\/([0-9]+)(\\/.*|)".r
  val MODULE = "\\/modules\\/([0-9]+)(\\/.*|)".r
  val CHART = "\\/charts\\/([0-9]+)".r
  val DATASET = "\\/datasets\\/([0-9]+)(\\/.*|)".r
  val FILE = "\\/files\\/([0-9]+)(\\/.*|)".r
  val TASK = "\\/tasks\\/(\\/.*|)".r
  val ARTIFACT = "\\/artifacts/([0-9]+)(\\/.*|)".r

  def handle(
    method: RequestMethod.T,
    request: HttpServletRequest, 
    output: HttpServletResponse
  ): Unit =
  {
    val response: Response = 
      try {
        val path = request.getPathInfo
        val startIdx = if(path.startsWith("/") || path.equals("")){ 1 } else { 0 }
        val pathComponents = path.split("/")
        logger.debug(s"API ${method} ${path} (${pathComponents.mkString("->")})")
        routes.handle(
          pathComponents, 
          startIdx, 
          method,
          Map.empty,
          request
        )
      } catch {
        case e: Throwable => 
          logger.error(e.getMessage + "\n" + e.getStackTrace.map { _.toString }.mkString("\n"))
          VizierErrorResponse(
            e.getClass.getCanonicalName(),
            e.getMessage()
          )
      }
    logger.trace(s"$response")
    response.write(output)
  }

  import RequestMethod.{ GET, PUT, POST, DELETE, OPTIONS }
  import Route.implicits._

  val routes: Route = 
    Route(
      GET -> ServiceDescriptorHandler,                    // service descriptor

      ///////////// Project Routes //////////
      "projects" -> Route(
        GET  -> ListProjectsHandler,                      // list projects
        POST -> JsonHandler[CreateProject],               // create a new project

        // "import" -> ???
        // "export" -> ???
        ":int:projectId" -> Route(
          GET -> GetProjectHandler,                       // export project
          POST -> JsonHandler[UpdateProject],             // update the project properties
          DELETE -> DeleteProjectHandler,                 // delete the project
          PUT -> JsonHandler[UpdateProject],              // update the project properties

      ///////////// Project/Branch Routes //////////
          "branches" -> Route(
            GET -> ListBranchesHandler,                   // list branches
            POST -> JsonHandler[CreateBranch],            // create a branch

            ":int:branchId" -> Route(
              GET -> GetBranchHandler,                    // get the branch
              DELETE -> DeleteBranchHandler,              // delete the branch
              PUT -> JsonHandler[UpdateBranch],           // update the branch properties

              "cancel" -> Route(
                POST -> CancelWorkflowHandler             // cancel the head workflow
              ),

      ///////////// Project/Branch/Workflow Routes //////////
              "workflows" -> Route(
                ":int:workflowId" -> Route(
                  GET -> GetWorkflowHandler,              // get the branch head workflow

                  "cancel" -> Route(
                    POST -> CancelWorkflowHandler         // cancel the specified workflow
                  ),

                  "sql" -> Route(
                    GET -> WorkflowSQLHandler,            // sql query on the tail of the specified branch
                  ),


        ///////////// Project/Branch/Workflow/Modules Routes //////////
                  "modules" -> Route(
                    GET -> GetAllModulesHandler,          // get all modules from the branch head
                    POST -> JsonHandler[AppendModule],    // append a module to the branch head
                  
                    ":int:modulePosition" -> Route(
                      GET -> GetModuleHandler,            // get the specified module from the branch head
                      POST -> JsonHandler[InsertModule],  // insert a module before the specified module
                      DELETE -> DeleteModuleHandler,      // delete the specified module
                      PUT -> JsonHandler[ReplaceModule],  // replace the specified module

                      "charts" -> Route(
                        ":int:artifactId" -> Route(
                          GET -> GetArtifactHandler.Typed(ArtifactType.CHART)
                        )
                      )
                    )
                  )
                ),
              ),

      ///////////// Project/Branch/Head Routes //////////
              "head" -> Route(
                GET -> GetWorkflowHandler,                // get the branch head workflow

                "cancel" -> Route(
                  POST -> CancelWorkflowHandler           // cancel the specified workflow
                ),
                "sql" -> Route(
                  GET -> WorkflowSQLHandler,              // sql query on the tail of the specified branch
                ),

      ///////////// Project/Branch/Head/Modules Routes //////////
                "modules" -> Route(
                  GET -> GetAllModulesHandler,            // get all modules from the branch head
                  POST -> JsonHandler[AppendModule],      // append a module to the branch head
                
                  ":int:modulePosition" -> Route(
                    GET -> GetModuleHandler,              // get the specified module from the branch head
                    POST -> JsonHandler[InsertModule],    // insert a module before the specified module
                    DELETE -> DeleteModuleHandler,        // delete the specified module
                    PUT -> JsonHandler[ReplaceModule],    // replace the specified module
                  )
                )
              )
            )
          ),

      ///////////// Project/Datasets Routes //////////
          "datasets" -> Route(
            POST -> JsonHandler[CreateDataset],           // create a new dataset in the data store
          
            ":int:artifactId" -> Route(
              GET -> GetArtifactHandler.Typed(ArtifactType.DATASET),

              "annotations" -> Route(
                GET -> GetArtifactHandler.Annotations,
              ),
              "descriptor" -> Route(
                GET -> GetArtifactHandler.Summary,
              ),
              "csv" -> Route(
                GET -> GetArtifactHandler.CSV,
              ),
            )
          ),

      ///////////// Project/Artifacts Routes //////////
          "artifacts" -> Route(
            ":int:artifactId" -> Route(
              GET -> GetArtifactHandler,

              "annotations" -> Route(
                GET -> GetArtifactHandler.Annotations,
              ),
              "descriptor" -> Route(
                GET -> GetArtifactHandler.Summary,
              ),
              "csv" -> Route(
                GET -> GetArtifactHandler.CSV,
              ),
            )
          ),

      ///////////// Project/Files Routes //////////
          "files" -> Route(
            POST -> CreateFileHandler,                    // create a new file in the data store

            ":int:fileId" -> Route(
              GET -> GetArtifactHandler.File,             // retrieve the specified file
              ":tail:subpath" -> Route(
                GET -> GetArtifactHandler.File,           // retrieve the specified file
              )
            )
          )

        )
      ),
      
      ///////////// Misc Routes //////////
      "tasks" -> Route(
        GET -> ListTasksHandler

        // ":int:taskId" -> ???
      ),

      "reload" -> Route(
        POST -> ReloadHandler // clear caches (deprecated)
      )
    )

  override def doGet(req: HttpServletRequest, output: HttpServletResponse) = 
    handle(GET, req, output)

  override def doPost(req: HttpServletRequest, output: HttpServletResponse) =
    handle(POST, req, output)

  override def doDelete(req: HttpServletRequest, output: HttpServletResponse) =
    handle(DELETE, req, output)

  override def doPut(req: HttpServletRequest, output: HttpServletResponse) =
    handle(PUT, req, output)

  override def doOptions(req: HttpServletRequest, output: HttpServletResponse) =
    handle(OPTIONS, req, output)

}

