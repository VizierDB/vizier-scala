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

import org.mimirdb.api.{ Request, Response }
import org.mimirdb.api.request.Query
import org.mimirdb.util.JsonUtils.stringifyJsonParseErrors
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

  def init(port: Int = DEFAULT_PORT, path: File = Vizier.basePath)
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

  object RequestMethod extends Enumeration
  {
    type T = Value
    val GET,
        PUT,
        POST,
        DELETE,
        OPTIONS = Value
  }
  import RequestMethod.{ GET, PUT, POST, DELETE, OPTIONS }

  def ellipsize(text: String, len: Int): String =
        if(text.size > len){ text.substring(0, len-3)+"..." } else { text }

  def fourOhFour(req: HttpServletRequest, output: HttpServletResponse)
  {
    logger.error(s"Vizier API ${req.getMethod} Not Handled: '${req.getPathInfo}' / '${req.getRequestURI}'")
    VizierErrorResponse(
      "NotFound",
      s"Vizier API ${req.getMethod} Not Handled: ${req.getPathInfo}",
      HttpServletResponse.SC_NOT_FOUND
    ).write(output)
  }

  def process(handler: Request)
             (request: HttpServletRequest, output: HttpServletResponse): Unit =
  {
    val response: Response = 
      try {
        handler.handle
      } catch {
        case e: Throwable => 
          logger.error(e.getMessage + "\n" + e.getStackTrace.map { _.toString }.mkString("\n"))
          VizierErrorResponse(
            e.getClass.getCanonicalName(),
            e.getMessage()
          )
      }
    logger.trace(s"${handler.getClass()} -> $response")
    response.write(output)
  }

  def processJson[Q <: Request](
    properties: (String,Identifier)*
  )(
    implicit format: Format[Q]
  ): ((HttpServletRequest, HttpServletResponse) => Unit) = 
  { 
    ( req: HttpServletRequest,
      output: HttpServletResponse) => {
        val text = scala.io.Source.fromInputStream(req.getInputStream).mkString 
        logger.debug(s"$text")
        val parsed: Either[Request, Response] = 
          try { 
            var parsed = Json.parse(text)
            if(!properties.isEmpty){
              parsed = JsObject(
                parsed.as[Map[String,JsValue]]
                  ++ properties.toMap.mapValues { JsNumber(_) }
              )
            }
            Left(parsed.as[Q])
          } catch {
            case e@JsResultException(errors) => {
              logger.error(e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("\n"))
              Right(VizierErrorResponse(
                e.getClass().getCanonicalName(),
                s"Error(s) parsing API request\n${ellipsize(text, 100)}\n"+stringifyJsonParseErrors(errors).mkString("\n")
              ))
            }
            case e:Throwable => {
              logger.error(e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("\n"))
              Right(VizierErrorResponse(
                e.getClass().getCanonicalName(),
                s"Error(s) parsing API request\n${ellipsize(text, 100)}\n"
              ))
            }
          }

        parsed match {
          case Left(request) => process(request)(req, output)
          case Right(response) => response.write(output)
        }
      }
  }

  def handle(method: RequestMethod.T, req: HttpServletRequest, output: HttpServletResponse)
  {
    def respond(responses: (RequestMethod.T, ((HttpServletRequest, HttpServletResponse) => Unit))*) = 
    {
      logger.info(s"Vizier API $method: ${req.getPathInfo}")
      if(VizierAPI.debug){
        output.setHeader("Access-Control-Allow-Origin", "*")
      }
      method match { 
        case OPTIONS => 
          output.setHeader("Access-Control-Allow-Headers", Seq(
            "content-type"
          ).mkString(", "))
          output.setHeader("Allow", responses.map { _._1.toString }.mkString(", "))
          output.setHeader("Access-Control-Allow-Methods", responses.map { _._1.toString }.mkString(", "))
        case _ => 
          logger.trace(s"Possible Responses: ${responses.map { _._1 }.mkString(", ")}")
          responses.find { _._1.equals(method) } match {
            case None                 => fourOhFour(req, output)
            case Some( (t, handler) ) => {
              logger.trace(s"Selected $t -> $handler")
              handler(req, output)
            }
          }
      }
    }

    try {
      req.getPathInfo match {
        case "/" => 
          respond(
            GET -> process(ServiceDescriptorRequest()) // service descriptor
          )
        case "/projects" => 
          respond(
            GET -> process(ListProjectsRequest()) // list projects
            ,
            POST -> processJson[CreateProject]() // create a new project
          )

        case "/reload" => 
          respond(
            POST -> process(ReloadRequest) // clear caches
          )


        case "/projects/import" => 
          ??? // import a project
        case PROJECT(projectId, "/export") => 
          ??? // export project

        case PROJECT(projectId, "") => 
          respond(
            GET -> process(GetProjectRequest(projectId.toLong)) // export project
            ,
            POST -> processJson[UpdateProject]("projectId" -> projectId.toLong) // update the project properties
            ,
            DELETE -> process(DeleteProject(projectId.toLong)) // delete the project
            ,
            PUT -> processJson[UpdateProject]("projectId" -> projectId.toLong) // update the project properties
          )
        case PROJECT(projectId, "/branches") => 
          respond(
            GET -> process(ListBranchesRequest(projectId.toLong)) // export project
            ,
            POST -> processJson[CreateBranch]("projectId" -> projectId.toLong) // create a branch
          )
        case PROJECT(projectId, BRANCH(branchId, "")) => 
          respond(
            GET -> process(GetBranchRequest(projectId.toLong, branchId.toLong)) // get the branch
            ,
            DELETE -> process(DeleteBranch(projectId.toLong, branchId.toLong)) // delete the branch
            ,
            PUT -> processJson[UpdateBranch]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong) // update the branch properties
          )
        case PROJECT(projectId, BRANCH(branchId, HEAD("/cancel"))) => 
          respond(
            POST -> process(CancelWorkflow(projectId.toLong, branchId.toLong, None)) // cancel the head workflow
          )
        case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, "/cancel"))) => 
          respond(
            POST -> process(CancelWorkflow(projectId.toLong, branchId.toLong, Some(workflowId.toLong))) // cancel the specified workflow
          )
        case PROJECT(projectId, BRANCH(branchId, "/head")) => 
          respond(
            GET -> process(GetWorkflowRequest(projectId.toLong, branchId.toLong, None)) // get the branch head workflow
          )
        case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, ""))) => 
          respond(
            GET -> process(GetWorkflowRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong))) // get the specified workflow
          )
        case PROJECT(projectId, BRANCH(branchId, HEAD("/modules"))) => 
          respond(
            GET -> process(GetAllModulesRequest(projectId.toLong, branchId.toLong, None)) // get the specified module from the branch head
            ,
            POST -> processJson[AppendModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong) // append a module to the branch head
          )
        case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, "/modules"))) => 
          respond(
            GET -> process(GetAllModulesRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong))) // get the specified module from the branch head
            ,
            POST -> processJson[AppendModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "workflowId" -> workflowId.toLong) // append a module to the branch head
          )
        case PROJECT(projectId, BRANCH(branchId, HEAD(MODULE(modulePosition, "")))) => 
          respond(
            GET -> process(GetModuleRequest(projectId.toLong, branchId.toLong, None, modulePosition.toInt)) // get the specified module from the branch head
            ,
            POST -> processJson[InsertModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "modulePosition" -> modulePosition.toInt) // insert a module before the specified module
            ,
            DELETE -> process(DeleteModule(projectId.toLong, branchId.toLong, modulePosition.toInt)) // delete the specified module
            ,
            PUT -> processJson[ReplaceModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "modulePosition" -> modulePosition.toInt) // replace the specified module
          )
        case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, MODULE(modulePosition, "")))) => 
          respond(
            GET -> process(GetModuleRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong), modulePosition.toInt))  // get the specified module
            ,
            POST -> processJson[InsertModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "modulePosition" -> modulePosition.toInt, "workflowId" -> workflowId.toLong) // insert a module before the specified module
            ,
            DELETE -> process(DeleteModule(projectId.toLong, branchId.toLong, modulePosition.toInt, Some(workflowId.toLong))) // delete the specified module
            ,
            PUT -> processJson[ReplaceModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "modulePosition" -> modulePosition.toInt, "workflowId" -> workflowId.toLong) // replace the specified module
          )
        case PROJECT(projectId, BRANCH(branchId, HEAD("/sql"))) => 
          respond(
            GET -> process(WorkflowSQLRequest(projectId.toLong, branchId.toLong, None, req.getParameter("query"))) // sql query on the tail of the specified branch
          )
        case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, "/sql"))) => 
          respond(
            GET -> process(WorkflowSQLRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong), req.getParameter("query"))) // sql query on the tail of the specified branch/workflow
          )
        case PROJECT(projectId, "/datasets") => 
          respond(
            POST -> processJson[CreateDataset]("projectId" -> projectId.toLong) // create a new dataset in the data store
          )
        case PROJECT(projectId, DATASET(datasetId, "")) =>
          respond(
            GET -> process(GetArtifactRequest(
              projectId.toLong, 
              datasetId.toLong, 
              expectedType = Some(ArtifactType.DATASET), 
              offset = Option(req.getParameter("offset")).map { _.split(",")(0).toLong },
              limit = Option(req.getParameter("limit")).map { _.toInt },
              forceProfiler = Option(req.getParameter("profile")).map { _.equals("true") }.getOrElse(false)
            )) // retrieve the specified dataset
          )
        case PROJECT(projectId, ARTIFACT(artifactId, "")) =>
          respond(
            GET -> process(GetArtifactRequest(
              projectId.toLong, 
              artifactId.toLong, 
              offset = Option(req.getParameter("offset")).map { _.split(",")(0).toLong },
              limit = Option(req.getParameter("limit")).map { _.toInt },
              forceProfiler = Option(req.getParameter("profile")).map { _.equals("true") }.getOrElse(false)
            )) // retrieve the specified dataset
          )
        case PROJECT(projectId, ARTIFACT(datasetId, "/annotations")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong)
                              .Annotations(
                                columnId = Option(req.getParameter("column")).map { _.toInt },
                                rowId = Option(req.getParameter("row"))
                              )) // retrieve the specified dataset with annotations
          )
        case PROJECT(projectId, DATASET(datasetId, "/annotations")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong)
                              .Annotations(
                                columnId = Option(req.getParameter("column")).map { _.toInt },
                                rowId = Option(req.getParameter("row"))
                              )) // retrieve the specified dataset with annotations
          )
        case PROJECT(projectId, ARTIFACT(datasetId, "/descriptor")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong).Summary) // retrieve the specified dataset's descriptor
          )
        case PROJECT(projectId, DATASET(datasetId, "/descriptor")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong).Summary) // retrieve the specified dataset's descriptor
          )
        case PROJECT(projectId, ARTIFACT(datasetId, "/csv")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong).CSV) // retrieve the specified dataset as a csv file
          )
        case PROJECT(projectId, ARTIFACT(datasetId, "/csv")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong).CSV) // retrieve the specified dataset as a csv file
          )
        case PROJECT(projectId, DATASET(datasetId, "/csv")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong).CSV) // retrieve the specified dataset as a csv file
          )
        case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, MODULE(modulePosition, CHART(chartId))))) => 
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, chartId.toLong, expectedType = Some(ArtifactType.CHART))) // get the specified module's chart
          )
        case PROJECT(projectId, "/files") => 
          respond(
            POST -> CreateFile.handler(projectId.toLong)// create a new file in the data store
          )
        case PROJECT(_, FILE(_, ".")) | PROJECT(_, FILE(_, "..")) =>
          fourOhFour(req, output)
        case PROJECT(projectId, FILE(fileId, "")) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, fileId.toLong).File()) // retrieve the specified file
          )
        case PROJECT(projectId, FILE(fileId, subpath)) =>
          respond(
            GET -> process(GetArtifactRequest(projectId.toLong, fileId.toLong).File(Some(subpath.substring(1/* trim off leading '/'*/)))) // retrieve the specified file
          )
        case "/tasks" | TASK("") =>
          respond(
            GET -> process(ListTasks)
          )
        case TASK(taskId) =>
          ??? // update the state of a running task
        case _ => fourOhFour(req, output)
      }
    } catch {
      // we should never hit this point... this indicates a bug
      case e: Throwable => 
        e.printStackTrace()
    }
  }



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