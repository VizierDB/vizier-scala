package info.vizierdb

import java.net.URL
import java.net.URLEncoder
import info.vizierdb.types.Identifier

class VizierURLs(
  base: URL,
  api: Option[URL]
)
{
  val serviceDescriptor = base
  val apiDoc = api.getOrElse { serviceDescriptor }

  def queryString(query: Map[String, String]): String =
    if(query.isEmpty){ "" }
    else { "?" + query.map { case (k, v) => 
        URLEncoder.encode(k, "UTF-8")+"="+
          URLEncoder.encode(v, "UTF-8")
      }.mkString("&")
    }

  def url(spec: String, query: Map[String,String] = Map.empty): URL =
    new URL(base, spec+queryString(query))
  def compose(base: URL, rest: String) =
    new URL(base.toString + rest)

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
  def getBranchHeadModule(projectId: Identifier, branchId: Identifier, moduleId: Identifier) =
    compose(getBranchHead(projectId, branchId), s"/modules/$moduleId")
  def getWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, moduleId: Identifier) =
    compose(getWorkflow(projectId, branchId, workflowId), s"/modules/$moduleId")
  def deleteBranchHeadModule(projectId: Identifier, branchId: Identifier, moduleId: Identifier) =
    getBranchHeadModule(projectId, branchId, moduleId)
  def deleteWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, moduleId: Identifier) =
    getWorkflowModule(projectId, branchId, workflowId, moduleId)
  def insertBranchHeadModule(projectId: Identifier, branchId: Identifier, moduleId: Identifier) =
    getBranchHeadModule(projectId, branchId, moduleId)
  def insertWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, moduleId: Identifier) =
    getWorkflowModule(projectId, branchId, workflowId, moduleId)
  def replaceBranchHeadModule(projectId: Identifier, branchId: Identifier, moduleId: Identifier) =
    getBranchHeadModule(projectId, branchId, moduleId)
  def replaceWorkflowModule(projectId: Identifier, branchId: Identifier, workflowId: Identifier, moduleId: Identifier) =
    getWorkflowModule(projectId, branchId, workflowId, moduleId)


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


  def getHeadChartView(projectId: Identifier, branchId: Identifier, moduleId: Identifier, chartId: Identifier) =
    compose(getBranchHeadModule(projectId, branchId, moduleId), s"/charts/$chartId")
  def getChartView(projectId: Identifier, branchId: Identifier, workflowId: Identifier, moduleId: Identifier, chartId: Identifier) =
    compose(getWorkflowModule(projectId, branchId, workflowId, moduleId), s"/charts/$chartId")

  def downloadFile(projectId: Identifier, fileId: Identifier, component: String) =
    compose(getProject(projectId), s"/files/$fileId/$component")
  def downloadFile(projectId: Identifier, fileId: Identifier) =
    compose(getProject(projectId), s"/files/$fileId")
  def uploadFile(projectId: Identifier) =
    compose(getProject(projectId), "/files")

  def getArtifact(projectId: Identifier, artifactId: Identifier) =
    url(s"projects/$projectId/artifacts/$artifactId")
}

