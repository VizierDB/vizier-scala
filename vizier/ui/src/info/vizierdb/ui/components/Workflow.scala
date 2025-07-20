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
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.network.BranchSubscription
import rx._
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.RxBufferBase
import info.vizierdb.ui.rxExtras.RxBufferWatcher
import info.vizierdb.ui.network.ModuleSubscription
import info.vizierdb.types.ArtifactType
import info.vizierdb.ui.widgets.FontAwesome

class Workflow(val subscription: BranchSubscription, val project: Project)
              (implicit owner: Ctx.Owner)
{

  val moduleViews = 
    subscription.modules
                .rxMap { module => new Module(module) }

  val moduleViewsWithEdits = 
    new TentativeEdits(moduleViews, project, this)

  val moduleNodes =
    RxBufferView(div(`class` := "module_list").render, 
      moduleViewsWithEdits.rxMap { element =>
        div(
          element.root,
          StandardInterModule(element)
        ).render
      }
    )

  // DEBUG: Automatically add a tentative module
  // {
  //   subscription.awaitingReSync.triggerLater { f => 
  //     if(!f) { moduleViewsWithEdits.insertInspector(2) }
  //   }
  // }

  def StandardInterModule(prevElement: WorkflowElement): Frag =
    InterModule(
      button(
        FontAwesome("pencil-square-o"),
        onclick := { _:dom.Event => 
          moduleViewsWithEdits.insertTentativeAfter(prevElement)
                              .setDefaultModule("docs","markdown")
                              .scrollIntoView()
        }
      ).render,
      button(
        FontAwesome("plus"),
        onclick := { _:dom.Event => 
          moduleViewsWithEdits.insertTentativeAfter(prevElement)
                              .scrollIntoView()
        }
      ).render,
      // Disabled for 2.0 by OK:
      //   The inspector presently looks like shit.  It's not critical for the
      //   2.0 release, so we're punting it.  Still... let's not give folks access
      //   to something that still  looks like shit.
      // button(
      //   FontAwesome("binoculars"),
      //   onclick := { _:dom.Event => 
      //     moduleViewsWithEdits.insertInspectorAfter(prevElement)
      //                         .scrollIntoView()
      //   }
      // ).render
    )

  /**
   * A helper method to create divs representing inter-module separators
   */
  def InterModule(buttons: dom.html.Button*): Frag =
    div(`class` := "inter_module",
      div(
        `class` := "elements", 
        span(`class` := "separator", "———"),
        buttons,
        span(`class` := "separator", "———"),
      )
    )

  /**
   * The root DOM node of the workflow
   * 
   * Note.  This should closely mirror [[StaticWorkflow]].  Any CSS-related changes applied here
   * should be propagated there as well.
   */
  val root = 
    div(`class` := "workflow_content",
      Rx { 
        if(subscription.awaitingReSync()) { div("Syncing workflow...") } 
        else { 
          div(`class` := "first",
            InterModule(
              button(
                FontAwesome("plus"),
                onclick := { _:dom.Event => moduleViewsWithEdits.prependTentative() }
              ).render
            ),
            moduleViewsWithEdits
              .rxLength
              .map { 
                case 0 => div(`class` := "hint",
                              "↑", br(), "Click here to add a module")
                case _ => div()
              }.reactive
          )
        }
      }.reactive,
      moduleNodes.root,
    )
}

