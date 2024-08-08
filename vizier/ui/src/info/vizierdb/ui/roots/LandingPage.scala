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

import play.api.libs.json._
import org.scalajs.dom.document
import scalatags.JsDom.all._
import rx._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.util.Logging
import org.scalajs.dom
import info.vizierdb.serialized
import info.vizierdb.serializers._
import scala.util.{ Success, Failure }
import info.vizierdb.ui.Vizier
import info.vizierdb.nativeTypes
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome


object LandingPage
  extends Logging
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
  class ProjectView()(implicit owner: Ctx.Owner)
  {
    val projects = Var[Option[serialized.ProjectList]](None)
    val projectNameField = Var[Option[dom.html.Input]](None)
    loadProjectList()
    
    def loadProjectList(): Unit =
    {
      println("A");
      Vizier.api.projectList()
        .onComplete {
          case Success(result) => 
            println("B");
            projects() = Some(result)
          case Failure(ex) =>
            println("C");
            Vizier.error(ex.toString())
        }
    }


    def createProject(name: String): Unit =
    {
      Vizier.api.projectCreate(
        serialized.PropertyList(
          "name" -> JsString(name)
        )
      ) .onComplete {
          case Success(result) => 
            dom.window.location.href = s"project.html?project=${result.id}"
          case Failure(ex) =>
            Vizier.error(ex.toString())
        }
    }

    def createProjectButtonClick(input: dom.html.Input): Unit =
    {
      createProject(input.value)
      projectNameField() = None
    }

    val root =
      projects.map {
        case Some(serialized.ProjectList(projects)) => 
          div(`class` := "project_list_wrapper",
            h2("Projects"),
            ul(`class` := "project_list",
              projects.zipWithIndex.map { case (projectRef, idx) =>
                li((if(idx % 2 == 0) { `class` := "even" } else { `class` := "odd" }),
                  a(
                    href := s"project.html?project=${projectRef.id}",
                    span(
                      `class` := "project_name",
                      (
                        projectRef("name")
                          .flatMap { _.asOpt[String] }
                          .getOrElse { "Untitled Project" }
                      ):String
                    ),
                  ),
                  span(
                    `class` := "dates",
                    div(`class` := "date_valign",
                      div(`class` := "created", "Created: ", nativeTypes.formatDate(projectRef.createdAt)),
                      div(`class` := "modified", "Modified: ", nativeTypes.formatDate(projectRef.lastModifiedAt)),
                    )
                  ),
                  span(
                    `class` := "actions",
                    a(
                      FontAwesome("trash-o"),
                      href := "#",
                      onclick := { _:dom.Event =>
                        val name = projectRef("name").getOrElse { s"Untitled Project ${idx}" }
                        if( dom.window.confirm(s"Really delete '$name'?") ){
                          Vizier.api.projectDelete(projectRef.id)
                              .onComplete { 
                                case Success(_) => loadProjectList()
                                case Failure(ex) => Vizier.error(ex.toString())
                              }
                        }
                      }
                    )
                  )
                )
              }
            ),
            div(
              projectNameField.map {
                case None => 
                  div(
                    button(
                      `class` := "create_project",
                      onclick := { (_:dom.MouseEvent) => 
                        projectNameField() = 
                          Some(input(
                            `type` := "text", 
                            placeholder := "Project Name",
                            // Submit on enter key
                            onkeyup := { evt:dom.KeyboardEvent => 
                              if(evt.keyCode == 13){ 
                                evt.preventDefault()
                                createProjectButtonClick(
                                  evt.target.asInstanceOf[dom.html.Input]
                                )
                              }
                            },
                          ).render)
                        projectNameField.now.get.focus()
                      },
                      "+"
                    ),
                    (if(projects.isEmpty){
                      div(`class` := "hint",
                          "↑", br(), "Click here to create a project")
                    } else { div() })
                  )
                case Some(f) => 
                  div(`class` := "create_project_form", 
                    f,
                    button(
                      onclick := { (_:dom.MouseEvent) =>
                        createProjectButtonClick(f)
                      },
                      "Create"
                    )
                  )
              }.reactive,
            )
          )
        case None => 
          div("Loading project list...")
      }.reactive
  }

  class ScriptView()(implicit owner: Ctx.Owner)
  {
    val scripts = Var[Option[serialized.VizierScriptList]](None)
    loadScriptList()
    
    def loadScriptList(): Unit =
    {
      Vizier.api.scriptListScripts()
        .onComplete {
          case Success(result) => 
            scripts() = Some(result)
          case Failure(ex) =>
            Vizier.error(ex.toString())
        }
    }

    val root =
      Rx {
        scripts() match 
        {
          case Some(serialized.VizierScriptList(scripts)) =>
            if(scripts.size <= 0){ div() }
            else {
              div(`class` := "project_list_wrapper",
                h2("Scripts"),
                ul(`class` := "project_list",
                  scripts.zipWithIndex.map { case (script, idx) => 
                    li((if(idx % 2 == 0) { `class` := "even" } else { `class` := "odd" }),
                      a(href := "script.html?script="+script.id, 
                        span(`class` := "project_name", script.name)
                      ),
                      span(
                        `class` := "actions",
                        a(
                          FontAwesome("trash-o"),
                          href := "#",
                          onclick := { _:dom.Event =>
                            val name = script.name
                            if( dom.window.confirm(s"Really delete '$name'?") ){
                              Vizier.api.scriptDeleteScript(script.id.toString)
                                  .onComplete { 
                                    case Success(_) => loadScriptList()
                                    case Failure(ex) => Vizier.error(ex.toString())
                                  }
                            }
                          }
                        )
                      )
                    )
                  }
                )
              )
            }
          case None => 
            div("Loading scripts...")
        }
      }.reactive
  }


  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectList = new ProjectView()
    val scriptList = new ScriptView()

    val root = div(id := "project_list_content",
      projectList.root,
      scriptList.root,
    ).render

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild( root )
      OnMount.trigger(document.body)
    })
  }
}