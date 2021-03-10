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
package info.vizierdb.catalog.serialized

import play.api.libs.json._
import java.time.ZonedDateTime
import info.vizierdb.util.HATEOAS

case class WorkflowSummary(
  id: String,
  createdAt: String,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  links: HATEOAS.T
)
{
  def toDescription(
    state: Int,
    modules: Seq[ModuleDescription],
    datasets: Seq[JsObject],
    dataobjects: Seq[JsObject],
    readOnly: Boolean,
    tableOfContents: Option[Seq[TableOfContentsEntry]],
    newLinks: HATEOAS.T
  ): WorkflowDescription =
    WorkflowDescription(
      id = id,
      createdAt = createdAt,
      action = action,
      packageId = packageId,
      commandId = commandId,
      state = state,
      modules = modules,
      datasets = datasets,
      dataobjects = dataobjects,
      readOnly = readOnly,
      tableOfContents = tableOfContents,
      links = HATEOAS.merge(links, newLinks)
    )
}

object WorkflowSummary
{
  implicit val format: Format[WorkflowSummary] = Json.format
}

case class WorkflowDescription(
  id: String,
  createdAt: String,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  state: Int,
  modules: Seq[ModuleDescription],
  datasets: Seq[JsObject],
  dataobjects: Seq[JsObject],
  readOnly: Boolean,
  tableOfContents: Option[Seq[TableOfContentsEntry]],
  links: HATEOAS.T
)

object WorkflowDescription
{
  implicit val format: Format[WorkflowDescription] = Json.format
}

