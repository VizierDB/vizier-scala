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
package info.vizierdb.ui.roots

import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.Vizier
import org.scalajs.dom
import org.scalajs.dom.document
import scala.util.{ Try, Success, Failure }
import info.vizierdb.ui.components.Project
import info.vizierdb.util.Logging
import info.vizierdb.ui.widgets.Spinner
import rx._
import info.vizierdb.ui.rxExtras.OnMount

object ProjectView
  extends Logging
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectId = 
      arguments.get("project")
               .getOrElse { Vizier.error("No Project ID specified") }
               .toLong
    val projectRequest = Vizier.api.projectGet(projectId)
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      try {
        projectRequest
            .onComplete { 
              case Success(response) => 
                Vizier.project() = Some(new Project(projectId).load(response))
                logger.debug(s"Project: ${Vizier.project.now.get}")
                document.addEventListener("keydown", { (evt:dom.KeyboardEvent) => 
                  if(evt.key == "Enter" && evt.ctrlKey){
                    Vizier.project.now.foreach { 
                      _.workflow.now.foreach {
                        _.moduleViewsWithEdits.saveAllCells()
                      }
                    }
                    evt.stopPropagation()
                  } else if (evt.keyCode == 116 /* f5 */) {
                    // disable reload https://github.com/VizierDB/vizier-scala/issues/159
                    evt.preventDefault()
                  // } else {
                  //   println(s"KEY: ${evt.keyCode}")
                  }
                })

                // The following bit can be uncommented for onLoad triggers
                // to automate development debugging
                // dom.window.setTimeout(
                //   () => {
                //     val workflow = 
                //       project.now
                //              .get
                //              .workflow
                //              .now
                //              .get
                //     val module = 
                //       workflow.moduleViewsWithEdits
                //               .prependTentative()
                //     // module.activeView.trigger { _ match {
                //     //   case Some(Left(commandlist)) =>
                //     //     commandlist.simulateClick("data", "load")
                //     //   case _ => 
                //     //     println("Waiting...")
                //     // }}
                //   },
                //   500
                // )

              case Failure(ex) => 
                Vizier.error(ex.toString)
            }

        document.body.appendChild(
          Rx { Vizier.project().map { _.root }
                        .getOrElse { Spinner(size = 30) } }.reactive
        )
        OnMount.trigger(document.body)
      } catch {
        case t: Throwable => logger.error(t.toString)
      }
    })
  }
}