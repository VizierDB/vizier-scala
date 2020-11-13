package info.vizierdb

import java.net.URL
import java.net.URLEncoder

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
    }.mkString("&")}

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
  def getProject(projectID: String) =
    url(s"projects/$projectID")
  def deleteProject(projectID: String) =
    getProject(projectID)
  def updateProject(projectID: String) =
    getProject(projectID)
  def listBranches(projectID: String) =
    url(s"projects/$projectID/branches")
  def createBranch(projectID: String) =
    listBranches(projectID)
  def getBranch(projectID: String, branchID: String) =
    url(s"projects/$projectID/branches/$branchID")
  def deleteBranch(projectID: String, branchID: String) =
    getBranch(projectID, branchID)
  def updateBranch(projectID: String, branchID: String) =
    getBranch(projectID, branchID)
  def getBranchHead(projectID: String, branchID: String) =
    url(s"projects/$projectID/branches/$branchID/head")
  def getWorkflow(projectID: String, branchID: String, workflowID: String) =
    url(s"projects/$projectID/branches/$branchID/workflows/$workflowID")
  def appendBranchHead(projectID: String, branchID: String) =
    compose(getBranchHead(projectID, branchID), "/modules")
  def appendWorkflow(projectID: String, branchID: String, workflowID: String) =
    compose(getWorkflow(projectID, branchID, workflowID), "/modules")
  def cancelBranchHead(projectID: String, branchID: String) =
    compose(getBranchHead(projectID, branchID), "/cancel")
  def cancelWorkflow(projectID: String, branchID: String, workflowID: String) =
    compose(getWorkflow(projectID, branchID, workflowID), "/cancel")
  def getBranchHeadModule(projectID: String, branchID: String, moduleID: String) =
    compose(getBranchHead(projectID, branchID), s"/modules/$moduleID")
  def getWorkflowModule(projectID: String, branchID: String, workflowID: String, moduleID: String) =
    compose(getWorkflow(projectID, branchID, workflowID), s"/modules/$moduleID")
  def deleteBranchHeadModule(projectID: String, branchID: String, moduleID: String) =
    getBranchHeadModule(projectID, branchID, moduleID)
  def deleteWorkflowModule(projectID: String, branchID: String, workflowID: String, moduleID: String) =
    getWorkflowModule(projectID, branchID, workflowID, moduleID)
  def insertBranchHeadModule(projectID: String, branchID: String, moduleID: String) =
    getBranchHeadModule(projectID, branchID, moduleID)
  def insertWorkflowModule(projectID: String, branchID: String, workflowID: String, moduleID: String) =
    getWorkflowModule(projectID, branchID, workflowID, moduleID)
  def replaceBranchHeadModule(projectID: String, branchID: String, moduleID: String) =
    getBranchHeadModule(projectID, branchID, moduleID)
  def replaceWorkflowModule(projectID: String, branchID: String, workflowID: String, moduleID: String) =
    getWorkflowModule(projectID, branchID, workflowID, moduleID)


  def getDataset(
    projectId: String, 
    datasetID: String, 
    forceProfiler: Boolean = false, 
    offset: Option[Int] = None, 
    limit: Option[Int] = None,
  ) = 
  {
    val query = (
      if(forceProfiler) { Map("profile" -> "true") } else { Map.empty }
      ++ offset.map { "offset" -> _.toString }
      ++ limit.map { "limit" -> _.toString }
    )
    url(s"projects/$projectId/artifacts/$datasetID", query)
  }
  def downloadDataset(projectId: String, datasetID: String) =
    compose(getDataset(projectId, datasetID), "/csv")
  def getDatasetCaveats(projectId: String, datasetID: String, columnID: Option[Int] = None, rowID: Option[String] = None) =
    compose(getDataset(projectId, datasetID), "/annotations"+queryString(
      (
        columnID.toSeq.map { "column" -> _.toString }
        ++ rowID.toSeq.map { "row" -> _.toString }
      ).toMap
    ))
  def getDatasetDescriptor(projectId: String, datasetID: String) =
    compose(getDataset(projectId, datasetID), "/descriptor")    


  def getHeadChartView(projectID: String, branchID: String, moduleID: String, chartID: String) =
    compose(getBranchHeadModule(projectID, branchID, moduleID), s"/charts/$chartID")
  def getChartView(projectID: String, branchID: String, workflowID: String, moduleID: String, chartID: String) =
    compose(getWorkflowModule(projectID, branchID, workflowID, moduleID), s"/charts/$chartID")

  def downloadFile(projectID: String, fileID: String) =
    compose(getProject(projectID), s"/files/$fileID")
  def uploadFile(projectID: String) =
    compose(getProject(projectID), "/files")

  def getArtifact(projectId: String, artifactID: String) =
    url(s"projects/$projectId/artifacts/$artifactID")
}

