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

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.serialized.ArtifactSummary
import info.vizierdb.types._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.ScrollIntoView
import info.vizierdb.serialized.ArtifactDescription
import scala.util.Success
import scala.util.Failure
import info.vizierdb.ui.Vizier
import info.vizierdb.serialized

/**
 * A user interface widget to help users to inspect the contents of artifacts.  These are
 * created by [[Module]] and typically attached to one as well.
 */
class ArtifactInspector(
  val workflow: Workflow,
  val id_attr: String
)(implicit owner: Ctx.Owner)
  extends WorkflowElement
  with NoWorkflowOutputs
  with ScrollIntoView.CanScroll
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val selected = Var[Either[(String, ArtifactDescription), String]](Right("Select an artifact..."))

  var nowShowing:Option[Identifier] = None
  val container = div(span()).render
  val tentativeModuleId: Option[Identifier] = None

  val root = 
    div(
      `class` := "module inspector", 
      id := id_attr,
      div(
        `class` := "menu",
        button(
          FontAwesome("trash"),
          onclick := { _:dom.Event => workflow.moduleViewsWithEdits.dropInspector(this) }
        ),
        div(`class` := "spacer")
      ),
      visibleArtifacts.map { m => 
        div(
          `class` := "artifact_picker",
          m.map { case (name, (summary, _)) =>
            div(`class` := "option", 
              FontAwesome(ArtifactType.icon(summary.category)),
              name,
              onclick := { _:dom.Event => 
                selected() = Right(s"Loading $name...")

                Vizier.api.artifactGet(
                  workflow.project.projectId, 
                  summary.id,
                  name = Some(name)
                ).onComplete {
                  case Success(descr) => selected() = Left(name -> descr)
                  case Failure(err) => Vizier.error(err.getMessage())
                }
              }
            )
          }.toSeq
        ) 
      }.reactive,
      selected.map { 
        case Left( (_, descr) ) => new DisplayArtifact(descr).root 
        case Right(msg) => span(msg).render
      }.reactive
    ).render
}