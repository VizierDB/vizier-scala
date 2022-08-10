package info.vizierdb.ui.network

import info.vizierdb.types._

case class ClientURLs(baseUrl: String)
{
  def makeUrl(path: String, query: (String, Any)*): String = 
    baseUrl + path + (
      if(query.forall { _._2 == null }) { "" }
      else{ "?" + query.filterNot { _._2 == null }.map { case (k, v) => k + "=" + v.toString }.mkString("&") }
    )

  def projectList = 
    makeUrl("index.html")
  def project(projectId: Identifier) = 
    makeUrl("project.html", "project" -> projectId)
  def spreadsheet(projectId: Identifier, datasetId: Identifier) = 
    makeUrl("spreadsheet.html", "project" -> projectId, "dataset" -> datasetId)
  def settings =
    makeUrl("settings.html")
  def settings(tab: String) =
    makeUrl(s"settings.html?tab=$tab")
  def artifact(projectId: Identifier, artifactId: Identifier, name: String = null) =
    makeUrl("artifact.html", "project" -> projectId, "artifact" -> artifactId, "name" -> name)
  def workflow(projectId: Identifier, branchId: Identifier, workflowId: Identifier) =
    makeUrl("workflow.html", "project" -> projectId, "branch" -> branchId, "workflow" -> workflowId)
}