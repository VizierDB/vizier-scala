package info.vizierdb

import java.io.File
import play.api.libs.json._
import info.vizierdb.types._
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ResourceHandler
import org.eclipse.jetty.server.handler.ContextHandler
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

  def init(port: Int = DEFAULT_PORT, path: File = Vizier.basePath)
  {
    if(server != null){ 
      throw new RuntimeException("Can't have two Vizier servers running in one JVM")
    }
    server = new Server(port)

    val webUI = new ResourceHandler()
    val vizierServlet = new ServletContextHandler(ServletContextHandler.SESSIONS)

    {
      // Static files for WebUI 
      val webUIPath = getClass().getClassLoader().getResource("web-ui")
      webUI.setDirectoriesListed(true)
      webUI.setResourceBase(webUIPath.toExternalForm())
      val webUIWrapper = new ContextHandler()
      webUIWrapper.setHandler(webUI)
      webUIWrapper.setContextPath("/web-ui")
      server.setHandler(webUI)
    }

    {
      // Actual API
      vizierServlet.setContextPath("/vizier-db")
      val holder = new ServletHolder(VizierServlet)
      holder.getRegistration()
            .setMultipartConfig(new MultipartConfigElement(
              /* location          = */ (new File(path, "temp")).toString,
              /* maxFileSize       = */ MAX_UPLOAD_SIZE.toLong,
              /* maxRequestSize    = */ MAX_UPLOAD_SIZE.toLong,
              /* fileSizeThreshold = */ MAX_FILE_MEMORY
            ))
      vizierServlet.addServlet(holder, "/*")
    }

    val handlerList = new HandlerCollection()
    handlerList.setHandlers(Array[Handler](
      webUI,
      vizierServlet
    ))
    server.setHandler(handlerList)

    urls = new VizierURLs(new URL(s"http://localhost:$port/vizier-db/api/v1/"), None)
    server.start()
    started = LocalDateTime.now()
  }
}

object VizierServlet
  extends HttpServlet 
  with LazyLogging
{
  val PREFIX   = "\\/vizier-db/api\\/v1(\\/.*)".r
  val PROJECT  = "\\/projects\\/([^/]+)(\\/.*|)".r
  val BRANCH   = "\\/branches\\/([^/]+)(\\/.*|)".r
  val HEAD = "\\/head(\\/.*|)".r
  val WORKFLOW = "\\/workflows\\/([^/]+)(\\/.*|)".r
  val MODULE = "\\/modules\\/([^/]+)(\\/.*|)".r
  val CHART = "\\/charts\\/([^/]+)".r
  val DATASET = "\\/datasets\\/([^/]+)(\\/.*|)".r
  val FILE = "\\/files\\/([^/]+)(\\/.*|)".r
  val TASK = "\\/tasks\\/(\\/.*|)".r
  val ARTIFACT = "\\/artifacts/([^/]+)(\\/.*|)".r

  def ellipsize(text: String, len: Int): String =
        if(text.size > len){ text.substring(0, len-3)+"..." } else { text }

  def fourOhFour(req: HttpServletRequest, output: HttpServletResponse)
  {
    output.setStatus(HttpServletResponse.SC_NOT_FOUND)
    logger.error(s"Vizier ${req.getMethod} Not Handled: ${req.getPathInfo}")
    ErrorResponse(
      s"Vizier ${req.getMethod} Not Handled: ${req.getPathInfo}",
      "Unknown Request:"+ req.getPathInfo, 
      Thread.currentThread().getStackTrace.map(_.toString).mkString("\n") 
    ).write(output)
  }

  def process(
    handler: Request,
    output: HttpServletResponse
  )
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
    response.write(output)
  }

  def processJson[Q <: Request](
    req: HttpServletRequest,
    output: HttpServletResponse,
    properties: (String,Identifier)*
  )(
    implicit format: Format[Q]
  ){ 
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
      case Left(request) => process(request, output)
      case Right(response) => response.write(output)
    }
  }

  override def doGet(req: HttpServletRequest, output: HttpServletResponse)
  {
    logger.info(s"Vizier GET ${req.getPathInfo}")
    req.getRequestURI match {
      case PREFIX(route) => 
        route match { 
          case "/" => 
            process(ServiceDescriptorRequest(), output) // service descriptor
          case "/projects" => 
            process(ListProjectsRequest(), output) // list projects
          case PROJECT(projectId, "") => 
            process(GetProjectRequest(projectId.toLong), output) // export project
          case PROJECT(projectId, "/export") => 
            ??? // export project
          case PROJECT(projectId, "/branches") => 
            process(ListBranchesRequest(projectId.toLong), output) // export project
          case PROJECT(projectId, BRANCH(branchId, "")) => 
            process(GetBranchRequest(projectId.toLong, branchId.toLong), output) // get the branch
          case PROJECT(projectId, BRANCH(branchId, "/head")) => 
            process(GetWorkflowRequest(projectId.toLong, branchId.toLong, None), output) // get the branch head workflow
          case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, ""))) => 
            process(GetWorkflowRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong)), output) // get the specified workflow
          case PROJECT(projectId, BRANCH(branchId, HEAD("/modules"))) => 
            process(GetAllModulesRequest(projectId.toLong, branchId.toLong, None), output) // get the specified module from the branch head
          case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, "/modules"))) => 
            process(GetAllModulesRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong)), output) // get the specified module from the branch head
          case PROJECT(projectId, BRANCH(branchId, HEAD(MODULE(moduleId, "")))) => 
            process(GetModuleRequest(projectId.toLong, branchId.toLong, None, moduleId.toLong), output) // get the specified module from the branch head
          case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, MODULE(moduleId, "")))) => 
            process(GetModuleRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong), moduleId.toLong), output)  // get the specified module
          case PROJECT(projectId, BRANCH(branchId, HEAD("/sql"))) => 
            process(WorkflowSQLRequest(projectId.toLong, branchId.toLong, None, req.getParameter("query")), output) // sql query on the tail of the specified branch
          case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, "/sql"))) => 
            process(WorkflowSQLRequest(projectId.toLong, branchId.toLong, Some(workflowId.toLong), req.getParameter("query")), output) // sql query on the tail of the specified branch/workflow
          case PROJECT(projectId, DATASET(datasetId, "")) =>
            process(GetArtifactRequest(
              projectId.toLong, 
              datasetId.toLong, 
              expectedType = Some(ArtifactType.DATASET), 
              offset = Option(req.getParameter("offset")).map { _.toLong },
              limit = Option(req.getParameter("limit")).map { _.toInt },
              forceProfiler = Option(req.getParameter("profile")).map { _.equals("true") }.getOrElse(false)
            ), output) // retrieve the specified dataset
          case PROJECT(projectId, DATASET(datasetId, "/annotations")) =>
            process(GetArtifactRequest(projectId.toLong, datasetId.toLong)
                    .Annotations(
                      columnId = Option(req.getParameter("column")).map { _.toInt },
                      rowId = Option(req.getParameter("row"))
                    ), output) // retrieve the specified dataset with annotations
          case PROJECT(projectId, DATASET(datasetId, "/descriptor")) =>
            process(GetArtifactRequest(projectId.toLong, datasetId.toLong).Summary, output) // retrieve the specified dataset's descriptor
          case PROJECT(projectId, DATASET(datasetId, "/csv")) =>
            process(GetArtifactRequest(projectId.toLong, datasetId.toLong).CSV, output) // retrieve the specified dataset as a csv file
          case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, MODULE(moduleId, CHART(chartId))))) => 
            process(GetArtifactRequest(projectId.toLong, chartId.toLong, expectedType = Some(ArtifactType.CHART)), output) // get the specified module's chart
          case PROJECT(projectId, FILE(fileId, "")) =>
            process(GetArtifactRequest(projectId.toLong, fileId.toLong).File, output) // retrieve the specified file
          case _ => fourOhFour(req, output)
        }
      case _ => fourOhFour(req, output)
    }
  }

  override def doPost(req: HttpServletRequest, output: HttpServletResponse)
  {
    logger.info(s"Vizier POST ${req.getPathInfo}")
    req.getRequestURI match {
      case PREFIX(route) => 
        route match { 
          case "/reload" => 
            process(ReloadRequest, output) // clear caches
          case "/projects" => 
            processJson[CreateProject](req, output) // create a new project
          case "/projects/import" => 
            ??? // import a project
          case PROJECT(projectId, "") => 
            processJson[UpdateProject](req, output, "projectId" -> projectId.toLong) // update the project properties
          case PROJECT(projectId, "/branches") => 
            processJson[CreateBranch](req, output, "projectId" -> projectId.toLong) // create a branch
          case PROJECT(projectId, BRANCH(branchId, "/head")) => 
            processJson[AppendModule](req, output, "projectId" -> projectId.toLong, "branchId" -> branchId.toLong) // append a module to the branch head
          case PROJECT(projectId, BRANCH(branchId, HEAD("/cancel"))) => 
            process(CancelWorkflow(projectId.toLong, branchId.toLong, None), output) // cancel the head workflow
          case PROJECT(projectId, BRANCH(branchId, WORKFLOW(workflowId, "/cancel"))) => 
            process(CancelWorkflow(projectId.toLong, branchId.toLong, Some(workflowId.toLong)), output) // cancel the specified workflow
          case PROJECT(projectId, BRANCH(branchId, HEAD(MODULE(moduleId, "")))) => 
            processJson[InsertModule](req, output, "projectId" -> projectId.toLong, "branchId" -> branchId.toLong, "moduleId" -> moduleId.toLong) // insert a module before the specified module
          case PROJECT(projectId, "/datasets") => 
            processJson[CreateDataset](req, output, "projectId" -> projectId.toLong) // create a new dataset in the data store
          case PROJECT(projectId, "/files") => 
            process(CreateFile(projectId.toLong, req.asInstanceOf[JettyRequest]), output)// create a new file in the data store
          case _ => fourOhFour(req, output)
        }
      case _ => fourOhFour(req, output)
    }
  }

  override def doDelete(req: HttpServletRequest, output: HttpServletResponse)
  {
    logger.info(s"Vizier DELETE ${req.getPathInfo}")
    req.getRequestURI match {
      case PREFIX(route) => 
        route match { 
          case PROJECT(projectId, "") => 
            process(DeleteProject(projectId.toLong), output) // delete the project
          case PROJECT(projectId, BRANCH(branchId, "")) => 
            process(DeleteBranch(projectId.toLong, branchId.toLong), output) // delete the branch
          case PROJECT(projectId, BRANCH(branchId, HEAD(MODULE(moduleId, "")))) => 
            process(DeleteModule(projectId.toLong, branchId.toLong, moduleId.toLong), output) // delete the specified module
          case _ => fourOhFour(req, output)
        }
      case _ => fourOhFour(req, output)
    }
  }

  override def doPut(req: HttpServletRequest, output: HttpServletResponse)
  {
    logger.info(s"Vizier PUT ${req.getPathInfo}")
    req.getRequestURI match {
      case PREFIX(route) => 
        route match { 
          case PROJECT(projectId, "") => 
            ??? // update the project properties
          case PROJECT(projectId, BRANCH(branchId, "")) => 
            ??? // update the branch properties
          case PROJECT(projectId, BRANCH(branchId, HEAD(MODULE(moduleId, "")))) => 
            ??? // replace the specified module
          case TASK(taskId) =>
            ??? // update the state of a running task
          case _ => fourOhFour(req, output)
        }
      case _ => fourOhFour(req, output)
    }
  }

}