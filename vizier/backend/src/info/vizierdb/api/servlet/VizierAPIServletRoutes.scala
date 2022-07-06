package info.vizierdb.api.servlet

/* this file is AUTOGENERATED by `scripts/build_routes` from `vizier/resources/vizier-routes.txt` */
/* DO NOT EDIT THIS FILE DIRECTLY */

import play.api.libs.json._
import java.net.URLDecoder
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import java.net.URLDecoder
import info.vizierdb.api.Response
import info.vizierdb.spark.caveats.DataContainer
import info.vizierdb.spark.caveats.CaveatFormat._
import org.mimirdb.caveats.Caveat
import info.vizierdb.types._
import info.vizierdb.api._
import info.vizierdb.api.response.{ CORSPreflightResponse, RawJsonResponse, NoContentResponse }
import info.vizierdb.api.handler._
import info.vizierdb.serialized
import info.vizierdb.serializers._

trait VizierAPIServletRoutes extends HttpServlet {

  def processResponse(request: HttpServletRequest, output: HttpServletResponse)(response: => Response): Unit
  def fourOhFour(response: HttpServletRequest): Response
  implicit def liftToOption[T](x: T): Option[T] = Some(x)
  def queryParameter(connection: JettyClientConnection, name:String): Option[String] =
    Option(connection.getParameter(name)).map { URLDecoder.decode(_, "UTF-8") }

  val ROUTE_PATTERN_4 = "/projects/([0-9]+)/export".r
  val ROUTE_PATTERN_5 = "/projects/([0-9]+)".r
  val ROUTE_PATTERN_6 = "/projects/([0-9]+)".r
  val ROUTE_PATTERN_7 = "/projects/([0-9]+)".r
  val ROUTE_PATTERN_8 = "/projects/([0-9]+)".r
  val ROUTE_PATTERN_9 = "/projects/([0-9]+)/branches".r
  val ROUTE_PATTERN_10 = "/projects/([0-9]+)/branches".r
  val ROUTE_PATTERN_11 = "/projects/([0-9]+)/branches/([0-9]+)".r
  val ROUTE_PATTERN_12 = "/projects/([0-9]+)/branches/([0-9]+)".r
  val ROUTE_PATTERN_13 = "/projects/([0-9]+)/branches/([0-9]+)".r
  val ROUTE_PATTERN_14 = "/projects/([0-9]+)/branches/([0-9]+)/cancel".r
  val ROUTE_PATTERN_15 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)".r
  val ROUTE_PATTERN_16 = "/projects/([0-9]+)/branches/([0-9]+)/head".r
  val ROUTE_PATTERN_17 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/cancel".r
  val ROUTE_PATTERN_18 = "/projects/([0-9]+)/branches/([0-9]+)/head/cancel".r
  val ROUTE_PATTERN_19 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/sql".r
  val ROUTE_PATTERN_20 = "/projects/([0-9]+)/branches/([0-9]+)/head/sql".r
  val ROUTE_PATTERN_21 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/graph".r
  val ROUTE_PATTERN_22 = "/projects/([0-9]+)/branches/([0-9]+)/head/graph".r
  val ROUTE_PATTERN_23 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules".r
  val ROUTE_PATTERN_24 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules".r
  val ROUTE_PATTERN_25 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules".r
  val ROUTE_PATTERN_26 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules".r
  val ROUTE_PATTERN_27 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/suggest".r
  val ROUTE_PATTERN_28 = "/projects/([0-9]+)/branches/([0-9]+)/head/suggest".r
  val ROUTE_PATTERN_29 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_30 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_31 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_32 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_33 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_34 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_35 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_36 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_37 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/thaw".r
  val ROUTE_PATTERN_38 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/thaw".r
  val ROUTE_PATTERN_39 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/thaw_one".r
  val ROUTE_PATTERN_40 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/thaw_one".r
  val ROUTE_PATTERN_41 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/freeze".r
  val ROUTE_PATTERN_42 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/freeze".r
  val ROUTE_PATTERN_43 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/freeze_one".r
  val ROUTE_PATTERN_44 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/freeze_one".r
  val ROUTE_PATTERN_45 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/charts/([0-9]+)".r
  val ROUTE_PATTERN_46 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/charts/([0-9]+)".r
  val ROUTE_PATTERN_47 = "/projects/([0-9]+)/datasets".r
  val ROUTE_PATTERN_48 = "/projects/([0-9]+)/datasets/([0-9]+)".r
  val ROUTE_PATTERN_49 = "/projects/([0-9]+)/datasets/([0-9]+)/annotations".r
  val ROUTE_PATTERN_50 = "/projects/([0-9]+)/datasets/([0-9]+)/descriptor".r
  val ROUTE_PATTERN_51 = "/projects/([0-9]+)/datasets/([0-9]+)/csv".r
  val ROUTE_PATTERN_52 = "/projects/([0-9]+)/datasets/([0-9]+)/file".r
  val ROUTE_PATTERN_53 = "/projects/([0-9]+)/artifacts/([0-9]+)".r
  val ROUTE_PATTERN_54 = "/projects/([0-9]+)/artifacts/([0-9]+)/annotations".r
  val ROUTE_PATTERN_55 = "/projects/([0-9]+)/artifacts/([0-9]+)/descriptor".r
  val ROUTE_PATTERN_56 = "/projects/([0-9]+)/artifacts/([0-9]+)/csv".r
  val ROUTE_PATTERN_57 = "/projects/([0-9]+)/artifacts/([0-9]+)/file".r
  val ROUTE_PATTERN_58 = "/projects/([0-9]+)/files".r
  val ROUTE_PATTERN_59 = "/projects/([0-9]+)/files/([0-9]+)".r
  val ROUTE_PATTERN_60 = "/projects/([0-9]+)/files/([0-9]+)/(.*)".r
  val ROUTE_PATTERN_61 = "/published/([^/]+)".r
  val ROUTE_PATTERN_65 = "/filesystem/(.*)".r

  override def doGet(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    lazy val jsonBody = connection.getJson.as[JsObject]
    processResponse(request, response) {
      request.getPathInfo match {
        case /*service:descriptor*/ "/" => RawJsonResponse(Json.toJson(ServiceDescriptor():serialized.ServiceDescriptor))
        case /*project:list*/ "/projects" => RawJsonResponse(Json.toJson(ListProjects():serialized.ProjectList))
        case /*project:export*/ ROUTE_PATTERN_4(projectId) => ExportProject(projectId = projectId.toLong)
        case /*project:get*/ ROUTE_PATTERN_5(projectId) => RawJsonResponse(Json.toJson(GetProject(projectId = projectId.toLong):serialized.ProjectDescription))
        case /*branch:list*/ ROUTE_PATTERN_9(projectId) => RawJsonResponse(Json.toJson(ListBranches(projectId = projectId.toLong):serialized.BranchList))
        case /*branch:get*/ ROUTE_PATTERN_11(projectId, branchId) => RawJsonResponse(Json.toJson(GetBranch(projectId = projectId.toLong, branchId = branchId.toLong):serialized.BranchDescription))
        case /*workflow:get*/ ROUTE_PATTERN_15(projectId, branchId, workflowId) => RawJsonResponse(Json.toJson(GetWorkflow(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong):serialized.WorkflowDescription))
        case /*workflow:head_get*/ ROUTE_PATTERN_16(projectId, branchId) => RawJsonResponse(Json.toJson(GetWorkflow(projectId = projectId.toLong, branchId = branchId.toLong):serialized.WorkflowDescription))
        case /*workflow:query*/ ROUTE_PATTERN_19(projectId, branchId, workflowId) => RawJsonResponse(Json.toJson(WorkflowSQL(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, query = queryParameter(connection, "query")):DataContainer))
        case /*workflow:head_query*/ ROUTE_PATTERN_20(projectId, branchId) => RawJsonResponse(Json.toJson(WorkflowSQL(projectId = projectId.toLong, branchId = branchId.toLong, query = queryParameter(connection, "query")):DataContainer))
        case /*workflow:graph*/ ROUTE_PATTERN_21(projectId, branchId, workflowId) => VizualizeWorkflow(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong)
        case /*workflow:graph*/ ROUTE_PATTERN_22(projectId, branchId) => VizualizeWorkflow(projectId = projectId.toLong, branchId = branchId.toLong)
        case /*workflow:modules*/ ROUTE_PATTERN_23(projectId, branchId, workflowId) => RawJsonResponse(Json.toJson(GetAllModules(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong):Seq[serialized.ModuleDescription]))
        case /*workflow:head_modules*/ ROUTE_PATTERN_24(projectId, branchId) => RawJsonResponse(Json.toJson(GetAllModules(projectId = projectId.toLong, branchId = branchId.toLong):Seq[serialized.ModuleDescription]))
        case /*workflow:suggest*/ ROUTE_PATTERN_27(projectId, branchId, workflowId) => RawJsonResponse(Json.toJson(SuggestCommand(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, before = queryParameter(connection, "before").map { _.toLong }, after = queryParameter(connection, "after").map { _.toLong }):Seq[serialized.PackageDescription]))
        case /*workflow:head_suggest*/ ROUTE_PATTERN_28(projectId, branchId) => RawJsonResponse(Json.toJson(SuggestCommand(projectId = projectId.toLong, branchId = branchId.toLong, before = queryParameter(connection, "before").map { _.toLong }, after = queryParameter(connection, "after").map { _.toLong }):Seq[serialized.PackageDescription]))
        case /*workflow:get_module*/ ROUTE_PATTERN_29(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(GetModule(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt):serialized.ModuleDescription))
        case /*workflow:head_get_module*/ ROUTE_PATTERN_30(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(GetModule(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt):serialized.ModuleDescription))
        case /*artifact:get_chart*/ ROUTE_PATTERN_45(projectId, branchId, workflowId, modulePosition, artifactId) => RawJsonResponse(Json.toJson(GetArtifact.typed(ArtifactType.CHART)(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt, artifactId = artifactId.toInt):serialized.ArtifactDescription))
        case /*artifact:head_get_chart*/ ROUTE_PATTERN_46(projectId, branchId, modulePosition, artifactId) => RawJsonResponse(Json.toJson(GetArtifact.typed(ArtifactType.CHART)(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt, artifactId = artifactId.toInt):serialized.ArtifactDescription))
        case /*artifact:get_dataset*/ ROUTE_PATTERN_48(projectId, artifactId) => RawJsonResponse(Json.toJson(GetArtifact.Dataset(projectId = projectId.toLong, artifactId = artifactId.toLong, offset = queryParameter(connection, "offset").map { _.toLong }, limit = queryParameter(connection, "limit").map { _.toInt }, profile = queryParameter(connection, "profile"), name = queryParameter(connection, "name")):serialized.DatasetDescription))
        case /*artifact:ds_get_annotations*/ ROUTE_PATTERN_49(projectId, artifactId) => RawJsonResponse(Json.toJson(GetArtifact.Annotations(projectId = projectId.toLong, artifactId = artifactId.toLong, column = queryParameter(connection, "column").map { _.toInt }, row = queryParameter(connection, "row")):Seq[Caveat]))
        case /*artifact:ds_get_summary*/ ROUTE_PATTERN_50(projectId, artifactId) => RawJsonResponse(Json.toJson(GetArtifact.Summary(projectId = projectId.toLong, artifactId = artifactId.toLong):serialized.ArtifactSummary))
        case /*artifact:ds_get_csv*/ ROUTE_PATTERN_51(projectId, artifactId) => GetArtifact.CSV(projectId = projectId.toLong, artifactId = artifactId.toLong)
        case /*artifact:ds_get_file*/ ROUTE_PATTERN_52(projectId, artifactId) => GetArtifact.File(projectId = projectId.toLong, artifactId = artifactId.toLong)
        case /*artifact:get*/ ROUTE_PATTERN_53(projectId, artifactId) => RawJsonResponse(Json.toJson(GetArtifact(projectId = projectId.toLong, artifactId = artifactId.toLong, offset = queryParameter(connection, "offset").map { _.toLong }, limit = queryParameter(connection, "limit").map { _.toInt }, profile = queryParameter(connection, "profile"), name = queryParameter(connection, "name")):serialized.ArtifactDescription))
        case /*artifact:get_annotations*/ ROUTE_PATTERN_54(projectId, artifactId) => RawJsonResponse(Json.toJson(GetArtifact.Annotations(projectId = projectId.toLong, artifactId = artifactId.toLong, column = queryParameter(connection, "column").map { _.toInt }, row = queryParameter(connection, "row")):Seq[Caveat]))
        case /*artifact:get_summary*/ ROUTE_PATTERN_55(projectId, artifactId) => RawJsonResponse(Json.toJson(GetArtifact.Summary(projectId = projectId.toLong, artifactId = artifactId.toLong):serialized.ArtifactSummary))
        case /*artifact:get_csv*/ ROUTE_PATTERN_56(projectId, artifactId) => GetArtifact.CSV(projectId = projectId.toLong, artifactId = artifactId.toLong)
        case /*artifact:get_file*/ ROUTE_PATTERN_57(projectId, artifactId) => GetArtifact.File(projectId = projectId.toLong, artifactId = artifactId.toLong)
        case /*artifact:download*/ ROUTE_PATTERN_59(projectId, artifactId) => GetArtifact.File(projectId = projectId.toLong, artifactId = artifactId.toLong)
        case /*artifact:download_subfile*/ ROUTE_PATTERN_60(projectId, artifactId, tail) => GetArtifact.File(projectId = projectId.toLong, artifactId = artifactId.toLong, tail = tail)
        case /*published:download_published*/ ROUTE_PATTERN_61(artifactName) => RawJsonResponse(Json.toJson(GetPublishedArtifact(artifactName = artifactName):serialized.ArtifactDescription))
        case /*service:list*/ "/tasks" => RawJsonResponse(Json.toJson(ListTasks():Seq[serialized.WorkflowSummary]))
        case /*fs:get_root*/ "/filesystem" => RawJsonResponse(Json.toJson(BrowseFilesystem():Seq[serialized.FilesystemObject]))
        case /*fs:get*/ ROUTE_PATTERN_65(path) => RawJsonResponse(Json.toJson(BrowseFilesystem(path = path):Seq[serialized.FilesystemObject]))
        case _ => fourOhFour(request)
      }
    }
  }
  override def doPost(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    lazy val jsonBody = connection.getJson.as[JsObject]
    processResponse(request, response) {
      request.getPathInfo match {
        case /*project:create*/ "/projects" => RawJsonResponse(Json.toJson(CreateProject(properties = (jsonBody \ "properties").as[serialized.PropertyList.T]):serialized.ProjectSummary))
        case /*project:import*/ "/projects/import" => RawJsonResponse(Json.toJson(ImportProject(file = connection.getPart("file")):serialized.ProjectDescription))
        case /*project:replace_props*/ ROUTE_PATTERN_6(projectId) => RawJsonResponse(Json.toJson(UpdateProject(projectId = projectId.toLong, properties = (jsonBody \ "properties").as[serialized.PropertyList.T], defaultBranch = (jsonBody \ "defaultBranch").asOpt[Identifier]):serialized.ProjectSummary))
        case /*branch:create*/ ROUTE_PATTERN_10(projectId) => RawJsonResponse(Json.toJson(CreateBranch(projectId = projectId.toLong, source = (jsonBody \ "source").asOpt[serialized.BranchSource], properties = (jsonBody \ "properties").as[serialized.PropertyList.T]):serialized.BranchSummary))
        case /*workflow:branch_cancel*/ ROUTE_PATTERN_14(projectId, branchId) => RawJsonResponse(Json.toJson(CancelWorkflow(projectId = projectId.toLong, branchId = branchId.toLong):serialized.WorkflowDescription))
        case /*workflow:cancel*/ ROUTE_PATTERN_17(projectId, branchId, workflowId) => RawJsonResponse(Json.toJson(CancelWorkflow(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong):serialized.WorkflowDescription))
        case /*workflow:head_cancel*/ ROUTE_PATTERN_18(projectId, branchId) => RawJsonResponse(Json.toJson(CancelWorkflow(projectId = projectId.toLong, branchId = branchId.toLong):serialized.WorkflowDescription))
        case /*workflow:append*/ ROUTE_PATTERN_25(projectId, branchId, workflowId) => RawJsonResponse(Json.toJson(AppendModule(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, packageId = (jsonBody \ "packageId").as[String], commandId = (jsonBody \ "commandId").as[String], arguments = (jsonBody \ "arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription))
        case /*workflow:head_append*/ ROUTE_PATTERN_26(projectId, branchId) => RawJsonResponse(Json.toJson(AppendModule(projectId = projectId.toLong, branchId = branchId.toLong, packageId = (jsonBody \ "packageId").as[String], commandId = (jsonBody \ "commandId").as[String], arguments = (jsonBody \ "arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription))
        case /*workflow:insert*/ ROUTE_PATTERN_31(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(InsertModule(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt, packageId = (jsonBody \ "packageId").as[String], commandId = (jsonBody \ "commandId").as[String], arguments = (jsonBody \ "arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription))
        case /*workflow:head_insert*/ ROUTE_PATTERN_32(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(InsertModule(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt, packageId = (jsonBody \ "packageId").as[String], commandId = (jsonBody \ "commandId").as[String], arguments = (jsonBody \ "arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription))
        case /*workflow:thaw_upto*/ ROUTE_PATTERN_37(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(ThawModules(thawUptoHere=true)(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:head_thaw_upto*/ ROUTE_PATTERN_38(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(ThawModules(thawUptoHere=true)(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:thaw_one*/ ROUTE_PATTERN_39(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(ThawModules(thawUptoHere=false)(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:head_thaw_one*/ ROUTE_PATTERN_40(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(ThawModules(thawUptoHere=false)(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:freeze_from*/ ROUTE_PATTERN_41(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(FreezeModules(freezeFromHere=true)(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:head_freeze_from*/ ROUTE_PATTERN_42(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(FreezeModules(freezeFromHere=true)(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:freeze_one*/ ROUTE_PATTERN_43(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(FreezeModules(freezeFromHere=false)(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:head_freeze_one*/ ROUTE_PATTERN_44(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(FreezeModules(freezeFromHere=false)(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*artifact:create_dataset*/ ROUTE_PATTERN_47(projectId) => RawJsonResponse(Json.toJson(CreateDataset(projectId = projectId.toLong, columns = (jsonBody \ "columns").as[Seq[serialized.DatasetColumn]], rows = (jsonBody \ "rows").as[Seq[serialized.DatasetRow]], name = (jsonBody \ "name").asOpt[String], properties = (jsonBody \ "properties").as[serialized.PropertyList.T], annotations = (jsonBody \ "annotations").asOpt[serialized.DatasetAnnotation]):serialized.ArtifactSummary))
        case /*artifact:upload*/ ROUTE_PATTERN_58(projectId) => RawJsonResponse(Json.toJson(CreateFile(projectId = projectId.toLong, file = connection.getPart("file")):serialized.ArtifactSummary))
        case /*service:reload*/ "/reload" => Reload(); NoContentResponse()
        case _ => fourOhFour(request)
      }
    }
  }
  override def doDelete(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    lazy val jsonBody = connection.getJson.as[JsObject]
    processResponse(request, response) {
      request.getPathInfo match {
        case /*project:delete*/ ROUTE_PATTERN_7(projectId) => DeleteProject(projectId = projectId.toLong); NoContentResponse()
        case /*branch:delete*/ ROUTE_PATTERN_12(projectId, branchId) => DeleteBranch(projectId = projectId.toLong, branchId = branchId.toLong); NoContentResponse()
        case /*workflow:delete*/ ROUTE_PATTERN_33(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(DeleteModule(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case /*workflow:head_delete*/ ROUTE_PATTERN_34(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(DeleteModule(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt):serialized.WorkflowDescription))
        case _ => fourOhFour(request)
      }
    }
  }
  override def doPut(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    lazy val jsonBody = connection.getJson.as[JsObject]
    processResponse(request, response) {
      request.getPathInfo match {
        case /*project:update*/ ROUTE_PATTERN_8(projectId) => RawJsonResponse(Json.toJson(UpdateProject(projectId = projectId.toLong, properties = (jsonBody \ "properties").as[serialized.PropertyList.T], defaultBranch = (jsonBody \ "defaultBranch").asOpt[Identifier]):serialized.ProjectSummary))
        case /*branch:update*/ ROUTE_PATTERN_13(projectId, branchId) => RawJsonResponse(Json.toJson(UpdateBranch(projectId = projectId.toLong, branchId = branchId.toLong, properties = (jsonBody \ "properties").as[serialized.PropertyList.T]):serialized.BranchSummary))
        case /*workflow:replace*/ ROUTE_PATTERN_35(projectId, branchId, workflowId, modulePosition) => RawJsonResponse(Json.toJson(ReplaceModule(projectId = projectId.toLong, branchId = branchId.toLong, workflowId = workflowId.toLong, modulePosition = modulePosition.toInt, packageId = (jsonBody \ "packageId").as[String], commandId = (jsonBody \ "commandId").as[String], arguments = (jsonBody \ "arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription))
        case /*workflow:head_replace*/ ROUTE_PATTERN_36(projectId, branchId, modulePosition) => RawJsonResponse(Json.toJson(ReplaceModule(projectId = projectId.toLong, branchId = branchId.toLong, modulePosition = modulePosition.toInt, packageId = (jsonBody \ "packageId").as[String], commandId = (jsonBody \ "commandId").as[String], arguments = (jsonBody \ "arguments").as[serialized.CommandArgumentList.T]):serialized.WorkflowDescription))
        case _ => fourOhFour(request)
      }
    }
  }

  override def doOptions(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    processResponse(request, response) {
      request.getPathInfo match {
        case "/" => CORSPreflightResponse("GET")
        case "/projects" => CORSPreflightResponse("GET", "POST")
        case "/projects/import" => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_4(projectId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_5(projectId) => CORSPreflightResponse("GET", "POST", "DELETE", "PUT")
        case ROUTE_PATTERN_9(projectId) => CORSPreflightResponse("GET", "POST")
        case ROUTE_PATTERN_11(projectId, branchId) => CORSPreflightResponse("GET", "DELETE", "PUT")
        case ROUTE_PATTERN_14(projectId, branchId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_15(projectId, branchId, workflowId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_16(projectId, branchId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_17(projectId, branchId, workflowId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_18(projectId, branchId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_19(projectId, branchId, workflowId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_20(projectId, branchId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_21(projectId, branchId, workflowId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_22(projectId, branchId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_23(projectId, branchId, workflowId) => CORSPreflightResponse("GET", "POST")
        case ROUTE_PATTERN_24(projectId, branchId) => CORSPreflightResponse("GET", "POST")
        case ROUTE_PATTERN_27(projectId, branchId, workflowId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_28(projectId, branchId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_29(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("GET", "POST", "DELETE", "PUT")
        case ROUTE_PATTERN_30(projectId, branchId, modulePosition) => CORSPreflightResponse("GET", "POST", "DELETE", "PUT")
        case ROUTE_PATTERN_37(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_38(projectId, branchId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_39(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_40(projectId, branchId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_41(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_42(projectId, branchId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_43(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_44(projectId, branchId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_45(projectId, branchId, workflowId, modulePosition, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_46(projectId, branchId, modulePosition, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_47(projectId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_48(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_49(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_50(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_51(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_52(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_53(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_54(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_55(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_56(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_57(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_58(projectId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_59(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_60(projectId, artifactId, tail) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_61(artifactName) => CORSPreflightResponse("GET")
        case "/tasks" => CORSPreflightResponse("GET")
        case "/reload" => CORSPreflightResponse("POST")
        case "/filesystem" => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_65(path) => CORSPreflightResponse("GET")
        case _ => fourOhFour(request)
      }
    }
  }
}
