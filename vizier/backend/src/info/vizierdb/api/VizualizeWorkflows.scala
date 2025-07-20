/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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
package info.vizierdb.api

import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.api.response.StringResponse
import info.vizierdb.viztrails.graph.WorkflowTrace
import info.vizierdb.api.handler._
import _root_.akka.http.scaladsl.model.ContentType

object VizualizeWorkflow
{
  def apply(
    projectId: Identifier,
    branchId: Identifier,
    workflowId: Option[Identifier] = None
  ): Response =
  {
    StringResponse(
      WorkflowTrace(
        projectId,
        branchId, 
        workflowId
      ),
      ContentType.parse("image/svg+xml").right.get
    )
  }
}

