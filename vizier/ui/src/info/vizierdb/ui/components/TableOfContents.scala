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
              .getOrElse { li(s"${module.command.packageId}.${module.command.commandId}") }

  def TentativeSummary(module: TentativeModule): Frag =
    li( 
      `class` := "tentative",
      span(
        module.editor.map { _.map { ed => s"${ed.packageId}.${ed.commandId}" }
                             .getOrElse { "new command" }:String }.reactive
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

  val root:Frag = 
    div(
      id := "table_of_contents", 
      `class` := "contents",
      h3(
        workflow.project.projectName.reactive,
        " ",
        FontAwesome("pencil-square-o")
      ),
      h4(
        "[",
        workflow.project.activeBranchName.reactive,
        "]"
      ),
      moduleNodes.root
    )
}
