package info.vizierdb.ui.network

import info.vizierdb.types._

case class ClientURLs(baseUrl: String)
{
  def makeUrl(path: String, query: (String, Any)*): String = 
    baseUrl + path + (
      if(query.isEmpty) { "" }
      else{ "?" + query.filterNot { _._2 == null }.map { case (k, v) => k + "=" + v.toString }.mkString("&") }
    )

  def projectList = 
    makeUrl("index.html")
  def project(projectId: Identifier) = 
    makeUrl("project.html", "project" -> projectId)
  def spreadsheet(projectId: Identifier, datasetId: Identifier) = 
    makeUrl("spreadsheet.html", "project" -> projectId, "dataset" -> datasetId)
}