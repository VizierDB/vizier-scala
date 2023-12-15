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
package info.vizierdb.serialized

import info.vizierdb.nativeTypes.JsObject
import info.vizierdb.nativeTypes.DateTime
import info.vizierdb.types.{ Identifier, ExecutionState }

case class WorkflowSummary(
  id: Identifier,
  createdAt: DateTime,
  action: String,
  actionModule: Option[Identifier],
  packageId: Option[String],
  commandId: Option[String],
)
{
  def toDescription(
    state: ExecutionState.T,
    modules: Seq[ModuleDescription],
    artifacts: Seq[ArtifactSummary],
    readOnly: Boolean,
  ): WorkflowDescription =
    WorkflowDescription(
      id = id,
      createdAt = createdAt,
      action = action,
      actionModule = actionModule,
      packageId = packageId,
      commandId = commandId,
      state = ExecutionState.translateToClassicVizier(state),
      statev2 = state,
      modules = modules,
      artifacts = artifacts,
      readOnly = readOnly,
    )
}

case class WorkflowDescription(
  id: Identifier,
  createdAt: DateTime,
  action: String,
  actionModule: Option[Identifier],
  packageId: Option[String],
  commandId: Option[String],
  state: Int,
  statev2: ExecutionState.T,
  modules: Seq[ModuleDescription],
  artifacts: Seq[ArtifactSummary],
  readOnly: Boolean,
)

