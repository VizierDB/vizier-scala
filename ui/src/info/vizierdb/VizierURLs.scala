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
package info.vizierdb

import info.vizierdb.types.Identifier

class VizierURLs(
  val base: String,
)
{
  val serviceDescriptor = base

  def urlencode(str: String, encoding: String) = 
    str

  def queryString(query: Map[String, String]): String =
    if(query.isEmpty){ "" }
    else { "?" + query.map { case (k, v) => 
        urlencode(k, "UTF-8")+"="+
          urlencode(v, "UTF-8")
      }.mkString("&")
    }

  def url(spec: String, query: Map[String,String] = Map.empty): String =
    base + "/" + spec + queryString(query)
  def compose(base: String, rest: String) =
    base + "/" + rest

  def createProject =
    listProjects
  def listProjects =
    url("projects")
  def importProject =
    url("projects/import")
  def getProject(projectId: Identifier) =
    url(s"projects/$projectId")
  def deleteProject(projectId: Identifier) =
    getProject(projectId)
  def updateProject(projectId: Identifier) =
    getProject(projectId)
  def listBranches(projectId: Identifier) =
    url(s"projects/$projectId/branches")
  def createBranch(projectId: Identifier) =
    listBranches(projectId)
  def getBranch(projectId: Identifier, branchId: Identifier) =
    url(s"projects/$projectId/branches/$branchId")
  def deleteBranch(projectId: Identifier, branchId: Identifier) =
    getBranch(projectId, branchId)
  def updateBranch(projectId: Identifier, branchId: Identifier) =
    getBranch(projectId, branchId)
  def getBranchHead(projectId: Identifier, branchId: Identifier) =
    url(s"projects/$projectId/branches/$branchId/head")
  def getWorkflow(projectId: Identifier, branchId: Identifier, workflowId: Identifier) =
    url(s"projects/$projectId/branches/$branchId/workflows/$workflowId")
  def appendBranchHead(projectId: Identifier, branchId: Identifier) =
    compose(getBranchHead(projectId, branchId), "/modules")
  def appendWorkflow(projectId: Identifier, branchId: Identifier, workflowId: Identifier) =
    compose(getWorkflow(projectId, branchId, workflowId), "/modules")
  def cancelBranchHead(projectId: Identifier, branchId: Identifier) =
    compose(getBranchHead(projectId, branchId), "/cancel")
  def cancelWorkflow(projectId: Identifier, branchId: Identifier, workflowId: Identifier) =
    compose(getWorkflow(projectId, branchId, workflowId), "/cancel")
  def getBranchHeadModule(projectId: Identifier, branchId: Identifier, modulePosition: Int) =
    compose(getBranchHead(projectId, branchId), s"/modules/$modulePosition")
  def getWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, modulePosition: Int) =
    compose(getWorkflow(projectId, branchId, workflowId), s"/modules/$modulePosition")
  def deleteBranchHeadModule(projectId: Identifier, branchId: Identifier, modulePosition: Int) =
    getBranchHeadModule(projectId, branchId, modulePosition)
  def deleteWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, modulePosition: Int) =
    getWorkflowModule(projectId, branchId, workflowId, modulePosition)
  def insertBranchHeadModule(projectId: Identifier, branchId: Identifier, modulePosition: Int) =
    getBranchHeadModule(projectId, branchId, modulePosition)
  def insertWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, modulePosition: Int) =
    getWorkflowModule(projectId, branchId, workflowId, modulePosition)
  def replaceBranchHeadModule(projectId: Identifier, branchId: Identifier, modulePosition: Int) =
    getBranchHeadModule(projectId, branchId, modulePosition)
  def replaceWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, modulePosition: Int) =
    getWorkflowModule(projectId, branchId, workflowId, modulePosition)
  def freezeBranchHeadModules(projectId: Identifier, branchId: Identifier, modulePosition: Int) =
    compose(getBranchHeadModule(projectId, branchId, modulePosition), "/freeze")
  def freezeWorkflowModules(projectId: Identifier, branchId: Identifier, workflowId: Identifier, modulePosition: Int) =
    compose(getWorkflowModule(projectId, branchId, workflowId, modulePosition), "/freeze")
  def thawBranchHeadModules(projectId: Identifier, branchId: Identifier, modulePosition: Int) =
    compose(getBranchHeadModule(projectId, branchId, modulePosition), "/thaw")
  def thawWorkflowModules(projectId: Identifier, branchId: Identifier, workflowId: Identifier, modulePosition: Int) =
    compose(getWorkflowModule(projectId, branchId, workflowId, modulePosition), "/thaw")
  def websocket =
    url("websocket").replaceAll("http://", "ws://")
                    .replaceAll("https://", "wss://")

  def getDataset(
    projectId: Identifier, 
    datasetId: Identifier, 
    forceProfiler: Boolean = false, 
    offset: Option[Long] = None, 
    limit: Option[Int] = None,
  ) = 
  {
    val query = (
      if(forceProfiler) { Map("profile" -> "true") } else { Map.empty }
      ++ offset.map { "offset" -> _.toString }
      ++ limit.map { "limit" -> _.toString }
    )
    url(s"projects/$projectId/artifacts/$datasetId", query)
  }
  def downloadDataset(projectId: Identifier, datasetId: Identifier) =
    compose(getDataset(projectId, datasetId), "/csv")
  def getDatasetCaveats(projectId: Identifier, datasetId: Identifier, columnID: Option[Int] = None, rowID: Option[String] = None) =
    compose(getDataset(projectId, datasetId), "/annotations"+queryString(
      (
        columnID.toSeq.map { "column" -> _.toString }
        ++ rowID.toSeq.map { "row" -> _.toString }
      ).toMap
    ))
  def getDatasetDescriptor(projectId: Identifier, datasetId: Identifier) =
    compose(getDataset(projectId, datasetId), "/descriptor")    


  def getHeadChartView(projectId: Identifier, branchId: Identifier, modulePosition: Int, chartId: Identifier) =
    compose(getBranchHeadModule(projectId, branchId, modulePosition), s"/charts/$chartId")
  def getChartView(projectId: Identifier, branchId: Identifier, workflowId: Identifier, modulePosition: Int, chartId: Identifier) =
    compose(getWorkflowModule(projectId, branchId, workflowId, modulePosition), s"/charts/$chartId")

  def downloadFile(projectId: Identifier, fileId: Identifier, component: String) =
    compose(getProject(projectId), s"/files/$fileId/$component")
  def downloadFile(projectId: Identifier, fileId: Identifier) =
    compose(getProject(projectId), s"/files/$fileId")
  def uploadFile(projectId: Identifier) =
    compose(getProject(projectId), "/files")

  def getArtifact(projectId: Identifier, artifactId: Identifier) =
    url(s"projects/$projectId/artifacts/$artifactId")
}

