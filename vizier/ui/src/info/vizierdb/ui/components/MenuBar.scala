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
package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.ShowModal
import info.vizierdb.ui.Vizier


class MenuBar(project: Project)(implicit owner: Ctx.Owner)
{  

  /**
   * A list of packages to provide help for.
   * 
   * Todo: These should be derived from dependencies in the notebook.
   */
  val packages = 
    Seq[(String,String)](
      "Vizier" -> "https://github.com/VizierDB/vizier-scala/wiki",
      "Spark" -> "https://spark.apache.org/docs/3.3.1/sql-programming-guide.html",
      "Sedona" -> "https://sedona.apache.org/1.5.0/"
    )

  def Menu(clazz: String, title: Modifier*)(items: Frag*) = 
  {
    var contents:Seq[Modifier] = title
    contents = (`class` := clazz) +: contents
    contents = contents :+ ul(items:_*)
    div(contents:_*)
  }

  def MenuItem(
    title: String, 
    action: () => Unit = { () => () }, 
    icon: String = null, 
    enabled: Boolean = true, 
    link: String = null
  ): Frag =
  {
    var contents = Seq[Modifier](title)

    if(!enabled){ contents = contents :+ (`class` := "disabled") }
    else if(link == null) {
      contents = contents :+ (onclick := { _:dom.Event => action() })
    }

    if(icon != null){ contents = FontAwesome(icon) +: contents }
  
    var body: Frag = li(contents)
    if(link != null && enabled){ body = a(href := link, target := "_blank", body) }

    return body
  }

  def Separator: Frag = li(`class` := "separator")

  val root = 
    tag("nav")(id := "menu_bar",

      ////////////////// Logo ////////////////// 

      a(`class` := "left item", href := "index.html", img(src := "vizier.svg")),
      
      ////////////////// Project Menu ////////////////// 
      Menu("left item", div(`class` := "text", project.projectName.reactive))(

        //////////////// Rename
        MenuItem("Rename...", { () => 
          val nameInput = 
            input(
              `type` := "text", 
              name := "project_name",
              value := project.projectName.now
            ).render
          ShowModal.confirm(
            label(
              `for` := "project_name",
              "Name: "
            ),
            nameInput
          ){ 
            project.setProjectName(nameInput.value)
          }
        }),

        //////////////// Rename
        MenuItem("Export Project...", { () => 
          dom.window.open(Vizier.api.makeUrl(s"/projects/${project.projectId}/export "), "_self")
        }),

        //////////////// Rename
        // View-only mode not supported yet
        // MenuItem("Present Project", { () => println("Present...") }),
      ),

      ////////////////// Branch Menu ////////////////// 
      Rx {
        val activeBranchId = project.activeBranch().getOrElse(-100)
        Menu("left item", FontAwesome("code-fork"))(
          (
            Seq(
              MenuItem("Rename Branch...", { () =>
                val nameInput = 
                  input(
                    `type` := "text", 
                    name := "branch_name",
                    value := project.activeBranchName.now
                  ).render
                ShowModal.confirm(
                  label(
                    `for` := "project_name",
                    "Name: "
                  ),
                  nameInput
                ){ 
                  project.setActiveBranchName(nameInput.value)
                }
              }),
              MenuItem("Create Branch...", { () => 
                val nameInput = 
                  input(
                    `type` := "text", 
                    name := "branch_name",
                    value := (project.activeBranchName.now + " copy")
                  ).render
                ShowModal.confirm(
                  label(
                    `for` := "project_name",
                    "Name: "
                  ),
                  nameInput
                ){ 
                  project.branchActiveWorkflow(nameInput.value)
                }
              }),
              MenuItem("History", { () => 
                  ShowModal.acknowledge(new History(project).root)
                }, icon = "history"),
              Separator,
            ) ++ project.branches().map { case (id, branch) => 
              MenuItem(
                branch.name, 
                { () => project.setActiveBranch(id) }, 
                icon = if(id == activeBranchId){ "code-fork" } else { null }
              )
            } ++ Seq[Frag](
              Separator,
              Rx { 
                val projectId = project.projectId
                val branchId =project.activeBranch().getOrElse(-1)
                a(href := s"script.html?project=${projectId}&branch=${branchId}", target := "_blank", li("Publish as script..."))
              }.reactive
            )
          ):_*
        )
      }.reactive,

      ////////////////// Settings Menu ////////////////// 
      Menu("left item", FontAwesome("wrench"))(
        a(href := "settings.html", target := "vizier_settings", li("Settings")),
        a(href := "settings.html?tab=python", target := "vizier_settings", li("Python Settings")),
        MenuItem("Scala Settings", { () => println("Scala Settings") }, enabled = false),
        Separator,
        a(href := "http://localhost:4040", target := "_blank", li("Spark Dashboard")),
      ),

      ////////////////// Help Menu ////////////////// \

      Menu(s"left item", FontAwesome("question-circle"))(
        packages.map { case (name, link) =>
          MenuItem(name, link = link, icon = "book")
        }
      ),

      ////////////////// Spacer ////////////////// 
      div(`class` := "spacer"),


      ////////////////// Run Menu ////////////////// 
      Rx { 
        val state = 
          project.workflow().map { _.moduleViewsWithEdits.state() }
                            .getOrElse { ExecutionState.DONE }
        val connected = 
          project.branchSubscription.map { _.connected() }
                                    .getOrElse(false)
        val (icon, stateClass) = 
          if(connected) {
            (
              FontAwesome(state match {
                case ExecutionState.RUNNING   => "play-circle"
                case ExecutionState.ERROR     => "exclamation-circle"
                case ExecutionState.CANCELLED => "pause-circle"
                case _                        => "stop-circle"
              }),
              state.toString.toLowerCase
            )
          } else { (
            div(
              FontAwesome("plug"), 
              span(`class` := "text", "Disconnected!"),
              FontAwesome("plug"), 
            ),
            "disconnected"
          ) }

        val options = 
          if(connected){
            Seq(
              MenuItem("Stop Running", 
                { () => project.branchSubscription.get.Client.workflowCancel() }, 
                icon = "stop", 
                enabled = (state == ExecutionState.RUNNING)
              ),
              MenuItem("Freeze Everything", 
                { () => project.branchSubscription.get.Client.workflowFreezeFrom(0) }, 
                icon = "snowflake-o"
              ),
              MenuItem("Thaw Everything", 
                { () => project.branchSubscription.get.Client.workflowThawUpto(project.workflow.now.get.moduleViews.rxLength.now) }, 
                icon = "sun-o"
              ),
            )
          } else {
            Seq(
              MenuItem("Reconnect", 
                { () => project.branchSubscription.get.reconnect() }, 
                icon = "plug"
              ),
            )
          }

        Menu(s"right item state_$stateClass", icon)(options:_*)
      }.reactive,
    ).render

}
