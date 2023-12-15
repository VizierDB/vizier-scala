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
  def asset(name: String) =
    makeUrl(s"/assets/$name")

}