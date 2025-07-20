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
package info.vizierdb.ui.components

import rx._
import info.vizierdb.serialized
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.types._

class StaticWorkflow(projectId: Identifier, workflow: serialized.WorkflowDescription)(implicit owner: Ctx.Owner)
{
  val modules: Seq[(serialized.ModuleDescription, Module)] = 
    workflow.modules
  					.zipWithIndex
  					.map { case (moduleDescription, idx) => 
              (
                moduleDescription, 
    						new Module(
    							new ModuleSubscription(moduleDescription, Right(this), Var(idx))
    						)
              )
  					}

  val artifacts = 
    modules.foldLeft(Map[String, (serialized.ArtifactSummary, Module)]()) { 
      case (accum, (moduleDescription, module)) =>
        accum ++ moduleDescription.artifacts.map { artifact =>
          (artifact.name, (artifact, module))
        }.toMap
    }

  val tableOfContents =
    new TableOfContents(
      projectId = projectId,
      modules = RxBuffer.ofSeq(modules.map { _._2 }),
      Var[Map[String, (serialized.ArtifactSummary, Module)]](artifacts)
    )
  /**
   * The root DOM node of the workflow
   * 
   * Note.  This should closely mirrir [[Workflow]] and [[Project]].  Any CSS-related changes applied here
   * should be propagated there as well.
   */
  val root = 
    div(id := "project",
      div(`class` := "content",
        div(
          `class` := "table_of_contents",
          tableOfContents.root
        ),
        div(
          `class` := "workflow",
          div(`class` := "workflow_content",
            div(`class` := "module_list",
              modules.map { _._2.root }
            ), 
          )
        )
      )
    ).render

}