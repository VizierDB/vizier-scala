package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.types._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.ui.widgets.ShowModal


class MenuBar(project: Project)(implicit owner: Ctx.Owner)
{  



  def Menu(clazz: String, title: Modifier*)(items: Frag*) = 
  {
    var contents:Seq[Modifier] = title
    contents = (`class` := clazz) +: contents
    contents = contents :+ ul(items:_*)
    div(contents:_*)
  }

  def MenuItem(title: String, action: () => Unit, icon: String = null, enabled: Boolean = true): Frag =
  {
    var contents = Seq[Modifier](title)

    if(!enabled){ contents = contents :+ (`class` := "disabled") }
    else { contents = contents :+ (onclick := { _:dom.Event => action() })}

    if(icon != null){ contents = FontAwesome(icon) +: contents }
    
    li(contents)
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
          dom.window.open(project.api.makeUrl(s"/projects/${project.projectId}/export "), "_self")
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
            }
          ):_*
        )
      }.reactive,

      ////////////////// Settings Menu ////////////////// 
      Menu("left item", FontAwesome("wrench"))(
        MenuItem("Python Settings", { () => println("Python Settings") }, enabled = false),
        MenuItem("Scala Settings", { () => println("Scala Settings") }, enabled = false),
        Separator,
        a(href := "http://localhost:4040", target := "_blank", li("Spark Dashboard")),
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
              state match {
                case ExecutionState.RUNNING   => "play-circle"
                case ExecutionState.ERROR     => "exclamation-circle"
                case ExecutionState.CANCELLED => "pause-circle"
                case _                        => "stop-circle"
              },
              state.toString.toLowerCase
            )
          } else { ("plug", "disconnected") }


        Menu(s"right item state_$stateClass", FontAwesome(icon))(
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
      }.reactive,

      ////////////////// Help Menu ////////////////// 
      a(
        `class` := "right item", 
        href := "https://www.github.com/VizierDB/vizier-scala/wiki", 
        target := "_blank",
        FontAwesome("question-circle")
      ),
    ).render

}