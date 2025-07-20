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
package info.vizierdb.ui.roots

import rx._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.ui.Vizier
import org.scalajs.dom.document
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import scala.util.{ Try, Success, Failure }
import info.vizierdb.ui.components

object StaticWorkflow
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val branchIdMaybe = arguments.get("branch").map { _.toLong }
    val workflowIdMaybe = arguments.get("workflow").map { _.toLong }

    val branchId = 
      branchIdMaybe.map { Future(_) }
                   .getOrElse { 
                       Vizier.api.projectGet(projectId)
                             .map { _.defaultBranch }
                   }

    val workflowId =
      workflowIdMaybe.map { Future(_) }
                     .getOrElse { 
                       branchId.flatMap { Vizier.api.branchGet(projectId, _) }
                               .map { _.head.id }
                     }

    val workflow = 
      branchId.flatMap { b => 
        workflowId.flatMap { w => 
          println(s"Getting workflow: ($projectId, $b, $w)")
          Vizier.api.workflowGet(projectId, b, w)
        }
      }

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      val root = Var[Frag](div(`class` := "display_workflow", Spinner(50)))

      document.body.appendChild(root.reactive)
      OnMount.trigger(document.body)

      workflow.onComplete { 
        case Success(w) => 
          println(s"Got workflow: $w")
          root() = new components.StaticWorkflow(projectId, w).root
        case Failure(err) => Vizier.error(err.getMessage())
      }
    })
  }

}