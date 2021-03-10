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
package info.vizierdb.api.servlet

/* this file is AUTOGENERATED by `sbt routes` from `src/main/resources/vizier-routes.txt` */
/* DO NOT EDIT THIS FILE DIRECTLY */

import play.api.libs.json._
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.mimirdb.api.Response
import info.vizierdb.types._
import info.vizierdb.api._
import info.vizierdb.api.response.CORSPreflightResponse
import info.vizierdb.api.handler._

trait VizierAPIServletRoutes extends HttpServlet {

  def processResponse(request: HttpServletRequest, output: HttpServletResponse)(response: => Response): Unit
  def fourOhFour(response: HttpServletRequest): Response

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
  val ROUTE_PATTERN_21 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules".r
  val ROUTE_PATTERN_22 = "/projects/([0-9]+)/branches/([0-9]+)/head/graph".r
  val ROUTE_PATTERN_23 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/graph".r
  val ROUTE_PATTERN_24 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules".r
  val ROUTE_PATTERN_25 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules".r
  val ROUTE_PATTERN_26 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules".r
  val ROUTE_PATTERN_27 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_28 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_29 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_30 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_31 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_32 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_33 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)".r
  val ROUTE_PATTERN_34 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)".r
  val ROUTE_PATTERN_35 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/thaw".r
  val ROUTE_PATTERN_36 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/thaw".r
  val ROUTE_PATTERN_37 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/charts/([0-9]+)".r
  val ROUTE_PATTERN_38 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/charts/([0-9]+)".r
  val ROUTE_PATTERN_39 = "/projects/([0-9]+)/branches/([0-9]+)/workflows/([0-9]+)/modules/([0-9]+)/freeze".r
  val ROUTE_PATTERN_40 = "/projects/([0-9]+)/branches/([0-9]+)/head/modules/([0-9]+)/freeze".r
  val ROUTE_PATTERN_41 = "/projects/([0-9]+)/datasets/([0-9]+)".r
  val ROUTE_PATTERN_42 = "/projects/([0-9]+)/datasets/([0-9]+)".r
  val ROUTE_PATTERN_43 = "/projects/([0-9]+)/datasets/([0-9]+)/annotations".r
  val ROUTE_PATTERN_44 = "/projects/([0-9]+)/datasets/([0-9]+)/descriptor".r
  val ROUTE_PATTERN_45 = "/projects/([0-9]+)/datasets/([0-9]+)/csv".r
  val ROUTE_PATTERN_46 = "/projects/([0-9]+)/artifacts/([0-9]+)".r
  val ROUTE_PATTERN_47 = "/projects/([0-9]+)/artifacts/([0-9]+)/annotations".r
  val ROUTE_PATTERN_48 = "/projects/([0-9]+)/artifacts/([0-9]+)/descriptor".r
  val ROUTE_PATTERN_49 = "/projects/([0-9]+)/artifacts/([0-9]+)/csv".r
  val ROUTE_PATTERN_50 = "/projects/([0-9]+)/files".r
  val ROUTE_PATTERN_51 = "/projects/([0-9]+)/files/([0-9]+)".r
  val ROUTE_PATTERN_52 = "/projects/([0-9]+)/files/([0-9]+)/(.+)".r

 override def doPost(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    processResponse(request, response) {
      request.getPathInfo match {
        case "/projects" => JsonHandler[CreateProject].handle(Map(), connection)
        case "/projects/import" => ImportProject.handle(Map(), connection)
        case ROUTE_PATTERN_6(projectId) => JsonHandler[UpdateProject].handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case ROUTE_PATTERN_10(projectId) => JsonHandler[CreateBranch].handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case ROUTE_PATTERN_14(projectId, branchId) => CancelWorkflowHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_17(projectId, branchId, workflowId) => CancelWorkflowHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong)), connection)
        case ROUTE_PATTERN_18(projectId, branchId) => CancelWorkflowHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_25(projectId, branchId, workflowId) => JsonHandler[AppendModule].handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong)), connection)
        case ROUTE_PATTERN_26(projectId, branchId) => JsonHandler[AppendModule].handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_29(projectId, branchId, workflowId, modulePosition) => JsonHandler[InsertModule].handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_30(projectId, branchId, modulePosition) => JsonHandler[InsertModule].handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_35(projectId, branchId, workflowId, modulePosition) => ThawModulesHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_36(projectId, branchId, modulePosition) => ThawModulesHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_39(projectId, branchId, workflowId, modulePosition) => FreezeModulesHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_40(projectId, branchId, modulePosition) => FreezeModulesHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_41(projectId, artifactId) => JsonHandler[CreateDataset].handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_50(projectId) => CreateFileHandler.handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case "/reload" => ReloadHandler.handle(Map(), connection)
        case _ => fourOhFour(request)
      }
    }
  }

 override def doGet(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    processResponse(request, response) {
      request.getPathInfo match {
        case "/" => ServiceDescriptorHandler.handle(Map(), connection)
        case "/projects" => ListProjectsHandler.handle(Map(), connection)
        case ROUTE_PATTERN_4(projectId) => ExportProject.handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case ROUTE_PATTERN_5(projectId) => GetProjectHandler.handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case ROUTE_PATTERN_9(projectId) => ListBranchesHandler.handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case ROUTE_PATTERN_11(projectId, branchId) => GetBranchHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_15(projectId, branchId, workflowId) => GetWorkflowHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong)), connection)
        case ROUTE_PATTERN_16(projectId, branchId) => GetWorkflowHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_19(projectId, branchId, workflowId) => WorkflowSQLHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong)), connection)
        case ROUTE_PATTERN_20(projectId, branchId) => WorkflowSQLHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_21(projectId, branchId, workflowId) => GetAllModulesHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong)), connection)
        case ROUTE_PATTERN_22(projectId, branchId) => VizualizeWorkflow.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_23(projectId, branchId, workflowId) => VizualizeWorkflow.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong)), connection)
        case ROUTE_PATTERN_24(projectId, branchId) => GetAllModulesHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_27(projectId, branchId, workflowId, modulePosition) => GetModuleHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_28(projectId, branchId, modulePosition) => GetModuleHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_37(projectId, branchId, workflowId, modulePosition, artifactId) => GetArtifactHandler.Typed(ArtifactType.CHART).handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_38(projectId, branchId, modulePosition, artifactId) => GetArtifactHandler.Typed(ArtifactType.CHART).handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_42(projectId, artifactId) => GetArtifactHandler.Typed(ArtifactType.DATASET).handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_43(projectId, artifactId) => GetArtifactHandler.Annotations.handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_44(projectId, artifactId) => GetArtifactHandler.Summary.handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_45(projectId, artifactId) => GetArtifactHandler.CSV.handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_46(projectId, artifactId) => GetArtifactHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_47(projectId, artifactId) => GetArtifactHandler.Annotations.handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_48(projectId, artifactId) => GetArtifactHandler.Summary.handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_49(projectId, artifactId) => GetArtifactHandler.CSV.handle(Map("projectId" -> JsNumber(projectId.toLong), "artifactId" -> JsNumber(artifactId.toLong)), connection)
        case ROUTE_PATTERN_51(projectId, fileId) => GetArtifactHandler.File.handle(Map("projectId" -> JsNumber(projectId.toLong), "fileId" -> JsNumber(fileId.toLong)), connection)
        case ROUTE_PATTERN_52(projectId, fileId, tail) => GetArtifactHandler.File.handle(Map("projectId" -> JsNumber(projectId.toLong), "fileId" -> JsNumber(fileId.toLong), "tail" -> JsString(tail)), connection)
        case "/tasks" => ListTasksHandler.handle(Map(), connection)
        case _ => fourOhFour(request)
      }
    }
  }

 override def doPut(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    processResponse(request, response) {
      request.getPathInfo match {
        case ROUTE_PATTERN_8(projectId) => JsonHandler[UpdateProject].handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case ROUTE_PATTERN_13(projectId, branchId) => JsonHandler[UpdateBranch].handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_33(projectId, branchId, workflowId, modulePosition) => JsonHandler[ReplaceModule].handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_34(projectId, branchId, modulePosition) => JsonHandler[ReplaceModule].handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case _ => fourOhFour(request)
      }
    }
  }

 override def doDelete(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    processResponse(request, response) {
      request.getPathInfo match {
        case ROUTE_PATTERN_7(projectId) => DeleteProjectHandler.handle(Map("projectId" -> JsNumber(projectId.toLong)), connection)
        case ROUTE_PATTERN_12(projectId, branchId) => DeleteBranchHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong)), connection)
        case ROUTE_PATTERN_31(projectId, branchId, workflowId, modulePosition) => DeleteModuleHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "workflowId" -> JsNumber(workflowId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case ROUTE_PATTERN_32(projectId, branchId, modulePosition) => DeleteModuleHandler.handle(Map("projectId" -> JsNumber(projectId.toLong), "branchId" -> JsNumber(branchId.toLong), "modulePosition" -> JsNumber(modulePosition.toLong)), connection)
        case _ => fourOhFour(request)
      }
    }
  }

  override def doOptions(request: HttpServletRequest, response: HttpServletResponse) = 
  {
    val connection = new JettyClientConnection(request, response)
    processResponse(request, response) {
      request.getPathInfo match {
        case ROUTE_PATTERN_48(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_4(projectId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_19(projectId, branchId, workflowId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_16(projectId, branchId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_35(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_27(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("GET", "POST", "DELETE", "PUT")
        case "/tasks" => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_39(projectId, branchId, workflowId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_24(projectId, branchId) => CORSPreflightResponse("GET", "POST")
        case ROUTE_PATTERN_52(projectId, fileId, tail) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_36(projectId, branchId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_38(projectId, branchId, modulePosition, artifactId) => CORSPreflightResponse("GET")
        case "/reload" => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_15(projectId, branchId, workflowId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_46(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_18(projectId, branchId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_37(projectId, branchId, workflowId, modulePosition, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_50(projectId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_44(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_43(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_23(projectId, branchId, workflowId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_41(projectId, artifactId) => CORSPreflightResponse("POST", "GET")
        case ROUTE_PATTERN_5(projectId) => CORSPreflightResponse("GET", "POST", "DELETE", "PUT")
        case ROUTE_PATTERN_9(projectId) => CORSPreflightResponse("GET", "POST")
        case "/projects" => CORSPreflightResponse("GET", "POST")
        case ROUTE_PATTERN_20(projectId, branchId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_21(projectId, branchId, workflowId) => CORSPreflightResponse("GET", "POST")
        case ROUTE_PATTERN_40(projectId, branchId, modulePosition) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_11(projectId, branchId) => CORSPreflightResponse("GET", "DELETE", "PUT")
        case ROUTE_PATTERN_28(projectId, branchId, modulePosition) => CORSPreflightResponse("GET", "POST", "DELETE", "PUT")
        case "/projects/import" => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_22(projectId, branchId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_14(projectId, branchId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_47(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_51(projectId, fileId) => CORSPreflightResponse("GET")
        case "/" => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_17(projectId, branchId, workflowId) => CORSPreflightResponse("POST")
        case ROUTE_PATTERN_49(projectId, artifactId) => CORSPreflightResponse("GET")
        case ROUTE_PATTERN_45(projectId, artifactId) => CORSPreflightResponse("GET")
        case _ => fourOhFour(request)
      }
    }
  }
}

