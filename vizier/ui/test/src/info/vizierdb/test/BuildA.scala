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
package info.vizierdb.test

import scala.scalajs.js
import scala.scalajs.js.JSON
import info.vizierdb.ui.network._
import info.vizierdb.types._
import info.vizierdb.serialized
import info.vizierdb.nativeTypes

object BuildA
{
  def Package(
    id: String,
    category: String = null,
    name: String = null
  )(
    commands: serialized.PackageCommand*
  ): serialized.PackageDescription =  
    serialized.PackageDescription(
      category = Option(category).getOrElse { id },
      id = id,
      name = Option(name).getOrElse { id },
      commands = js.Array(commands:_*)
    )

  def Command(
    id: String,
    name: String = null,
    suggest: Boolean = false
  )(
    parameters: (Int => serialized.ParameterDescription)*
  ): serialized.PackageCommand =  
    serialized.PackageCommand(
      id = id,
      name = Option(name).getOrElse { id },
      suggest = Some(suggest),
      parameters = parameters.zipWithIndex.map { case (p, idx) => p(idx) }
    )

  var nextModuleId = -1l
  def getNextModuleId = { nextModuleId += 1; nextModuleId }

  var nextArtifactId = -1l
  def getNextArtifactId = { nextArtifactId += 1; nextArtifactId }

  def Module(
    packageId: String,
    commandId: String,
    artifacts: Seq[(String, ArtifactType.T)] = Seq.empty,
    id: Identifier = getNextModuleId,
    state: ExecutionState.T = ExecutionState.DONE
  )(
    arguments: (String, nativeTypes.JsValue)*
  ) = 
    serialized.ModuleDescription(
      id = id.toString,
      moduleId = id,
      state = -1,
      statev2 = state,
      command = serialized.CommandDescription(
        packageId = packageId,
        commandId = commandId,
        arguments = serialized.CommandArgumentList(arguments:_*)
      ),
      text = s"$packageId.$commandId",
      outputs = serialized.ModuleOutputDescription(
        stdout = Seq.empty,
        stderr = Seq.empty,
      ),
      toc = None,
      timestamps = serialized.Timestamps(new js.Date()),
      artifacts = artifacts.map { case (name, t) =>
        val id = getNextArtifactId
        serialized.StandardArtifact(
          id = id,
          projectId = 1,
          key = id,
          name = name,
          category = t,
          objType = "dataset/view",
        )
      },
      resultId = Some(1),
      deleted = Seq.empty,
      inputs = Map.empty
    )

  def WorkflowByInserting(
    workflow: serialized.WorkflowDescription, 
    position: Int, 
    module: serialized.ModuleDescription,
  ) = 
    workflow.copy(
      modules = workflow.modules.patch(position, Seq(module), 0)
    )

  def WorkflowByAppending(
    workflow: serialized.WorkflowDescription, 
    module: serialized.ModuleDescription,
  ) = 
    workflow.copy(
      modules = workflow.modules :+ module
    )
}