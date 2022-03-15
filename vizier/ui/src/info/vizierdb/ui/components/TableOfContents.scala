package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome

class TableOfContents(workflow: Workflow)
                     (implicit owner: Ctx.Owner)
{

  def ModuleSummary(module: Module): Frag =
    module.toc.map { toc => 
                li(a(
                    href := s"#${module.id_attr}", toc.title,
                  ),
                  onmouseover := { _:dom.Event => module.highlight() = true },
                  onmouseout := { _:dom.Event => module.highlight() = false }
                ) 
              }
              .getOrElse { li(s"${module.subscription.packageId}.${module.subscription.commandId}") }

  def TentativeSummary(module: TentativeModule): Frag =
    li( 
      `class` := "tentative",
      span(
        module.editor.map { _.map { ed => s"${ed.packageId}.${ed.commandId}" }
                             .getOrElse { "New Module" }:String }.reactive
      )
    )


  val moduleNodes =
    RxBufferView(ul(), 
      workflow.moduleViewsWithEdits
              .rxMap { 
                  case Left(module) => ModuleSummary(module)
                  case Right(edit) => TentativeSummary(edit)
                }
    )

  val projectNameEditor = 
    Var[Option[dom.html.Input]](None)

  val branchList =
    div(`class` := "branch_list",
      Rx {
        val branches = workflow.project.branches()
        ul(
          branches.values.map { branch => 
            a(href := "#", branch.name, 
              onclick := { _:dom.Event => 
                            if(workflow.project.activeBranch.now != Some(branch.id)){
                              workflow.project.setActiveBranch(branch.id)
                            } else {
                              println(s"Already on branch ${branch.id}")
                            }
                          }
            )
          }.toSeq
        ),
      }.reactive
    ).render

  val root:Frag = 
    div(
      id := "table_of_contents", 
      `class` := "contents",
      h3(`class` := "project_name",
        Rx {
          projectNameEditor() match {
            case Some(ed) => 
              div(ed, button(
                FontAwesome("check"),
                onclick := { _:dom.Event => 
                  workflow.project.setProjectName(ed.value)
                  projectNameEditor() = None
                }
              ))
            case None => 
              div(
                workflow.project.projectName(),
                button(
                  FontAwesome("pencil-square-o"),
                  onclick := { _:dom.Event =>
                    projectNameEditor() = Some(
                      input(
                        value := workflow.project.projectName()
                      ).render
                    )
                  }
                )
              )
          }
        }.reactive,
        " ",

      ),
      h4(`class` := "branch_name",
        button(
          FontAwesome("code-fork"),
          " ",
          workflow.project.activeBranchName.reactive,
          onclick := { _:dom.Event => 
            branchList.classList.toggle("active")
          }
        )
      ),
      branchList,
      moduleNodes.root
    )
}
