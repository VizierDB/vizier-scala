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

import org.mimirdb.api.{ Request, Response, ErrorResponse }
import org.mimirdb.api.request.Query
import org.mimirdb.util.JsonUtils.stringifyJsonParseErrors
import java.net.URL
import java.sql.Time
import java.time.LocalDateTime
import info.vizierdb.api._
import com.amazonaws.services.codepipeline.model.Artifact

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

  lazy val WEB_UI_URL = getClass().getClassLoader().getResource("web-ui")

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
      val webUI = new ServletHolder("default", new DefaultServlet)
      webUI.setInitParameter("resourceBase", 
        WEB_UI_URL.toExternalForm()
      )
      webUI.setInitParameter("dirAllowed", "true")
      webUI.setInitParameter("pathInfoOnly", "true")
      context.addServlet(webUI, "/*")
    }

    urls = new VizierURLs(new URL(s"http://localhost:$port/vizier-db/api/v1/"), None)
    server.start()
    // server.dump(System.err)
    started = LocalDateTime.now()
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
    output.setStatus(HttpServletResponse.SC_NOT_FOUND)
    logger.error(s"Vizier API ${req.getMethod} Not Handled: '${req.getPathInfo}' / '${req.getRequestURI}'")
    ErrorResponse(
      s"Vizier API ${req.getMethod} Not Handled: ${req.getPathInfo}",
      "Unknown Request:"+ req.getPathInfo, 
      Thread.currentThread().getStackTrace.map(_.toString).mkString("\n") 
    ).write(output)
  }

  def process(
    handler: Request
  ): ((HttpServletRequest, HttpServletResponse) => Unit) =
  {
    val response: Response = 
      try {
        handler.handle
      } catch {
        case e: Throwable => 
          logger.error(e.getMessage + "\n" + e.getStackTrace.map { _.toString }.mkString("\n"))
          ErrorResponse(
            e.getClass.getCanonicalName(),
            e.getMessage(),
            e.getStackTrace.map { _.toString }.mkString("\n")
          )
      }
    {
      (_:HttpServletRequest, output: HttpServletResponse) => 
        response.write(output)
    }
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
              Right(ErrorResponse(
                e.getClass().getCanonicalName(),
                s"Error(s) parsing API request\n${ellipsize(text, 100)}\n"+stringifyJsonParseErrors(errors).mkString("\n"),
                e.getStackTrace.map(_.toString).mkString("\n")
              ))
            }
            case e:Throwable => {
              logger.error(e.getMessage + "\n" + e.getStackTrace.map(_.toString).mkString("\n"))
              Right(ErrorResponse(
                e.getClass().getCanonicalName(),
                s"Error(s) parsing API request\n${ellipsize(text, 100)}\n",
                e.getStackTrace.map(_.toString).mkString("\n")
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
        case _ => 
          responses.find { _._1.equals(method) } match {
            case None                 => fourOhFour(req, output)
            case Some( (_, handler) ) => handler(req, output)
          }
      }
    }

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
          ,
          POST -> processJson[AppendModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong) // append a module to the branch head
        )
      case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, ""))) => 
        respond(
          GET -> process(GetWorkflowRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong))) // get the specified workflow
        )
      case PROJECT(projectId, BRANCH(branchId, HEAD("/modules"))) => 
        respond(
          GET -> process(GetAllModulesRequest(projectId.toLong, branchId.toLong, None)) // get the specified module from the branch head
        )
      case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, "/modules"))) => 
        respond(
          GET -> process(GetAllModulesRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong))) // get the specified module from the branch head
        )
      case PROJECT(projectId, BRANCH(branchId, HEAD(MODULE(moduleId, "")))) => 
        respond(
          GET -> process(GetModuleRequest(projectId.toLong, branchId.toLong, None, moduleId.toLong)) // get the specified module from the branch head
          ,
          POST -> processJson[InsertModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "moduleId" -> moduleId.toLong) // insert a module before the specified module
          ,
          DELETE -> process(DeleteModule(projectId.toLong, branchId.toLong, moduleId.toLong)) // delete the specified module
          ,
          PUT -> processJson[ReplaceModule]("projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "moduleId" -> moduleId.toLong) // replace the specified module
        )
      case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, MODULE(moduleId, "")))) => 
        respond(
          GET -> process(GetModuleRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong), moduleId.toLong))  // get the specified module
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
            offset = Option(req.getParameter("offset")).map { _.toLong },
            limit = Option(req.getParameter("limit")).map { _.toInt },
            forceProfiler = Option(req.getParameter("profile")).map { _.equals("true") }.getOrElse(false)
          )) // retrieve the specified dataset
        )
      case PROJECT(projectId, DATASET(datasetId, "/annotations")) =>
        respond(
          GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong)
                            .Annotations(
                              columnId = Option(req.getParameter("column")).map { _.toInt },
                              rowId = Option(req.getParameter("row"))
                            )) // retrieve the specified dataset with annotations
        )
      case PROJECT(projectId, DATASET(datasetId, "/descriptor")) =>
        respond(
          GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong).Summary) // retrieve the specified dataset's descriptor
        )
      case PROJECT(projectId, DATASET(datasetId, "/csv")) =>
        respond(
          GET -> process(GetArtifactRequest(projectId.toLong, datasetId.toLong).CSV) // retrieve the specified dataset as a csv file
        )
      case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, MODULE(moduleId, CHART(chartId))))) => 
        respond(
          GET -> process(GetArtifactRequest(projectId.toLong, chartId.toLong, expectedType = Some(ArtifactType.CHART))) // get the specified module's chart
        )
      case PROJECT(projectId, "/files") => 
        respond(
          POST -> process(CreateFile(projectId.toLong, req.asInstanceOf[JettyRequest]))// create a new file in the data store
        )
      case PROJECT(projectId, FILE(fileId, "")) =>
        respond(
          GET -> process(GetArtifactRequest(projectId.toLong, fileId.toLong).File) // retrieve the specified file
        )
      case TASK(taskId) =>
        ??? // update the state of a running task
      case _ => fourOhFour(req, output)
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