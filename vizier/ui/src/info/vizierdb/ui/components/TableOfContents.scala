package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.types._

class TableOfContents(workflow: Workflow)
                     (implicit owner: Ctx.Owner)
{

  def ModuleSummary(module: Module): Frag =
    Rx {
      val clazz = s"${module.subscription.state().toString.toLowerCase}_state"
      module.toc.map { toc => 
                      li(`class` := clazz,
                        a(
                          href := s"#${module.id_attr}", toc.title,
                        ),
                        onmouseover := { _:dom.Event => module.highlight() = true },
                        onmouseout := { _:dom.Event => module.highlight() = false }
                      )
                    }
                    .getOrElse { 
                      li(
                        `class` := clazz,
                        s"${module.subscription.packageId}.${module.subscription.commandId}"
                      ) 
                    }
    }.reactive


  def TentativeSummary(module: TentativeModule): Frag =
    li( 
      `class` := "tentative",
      span(
        module.editor.map { _.map { ed => s"${ed.packageId}.${ed.commandId}" }
                             .getOrElse { "New Module" }:String }.reactive
      )
    )


  val moduleNodes =
    RxBufferView(ol(), 
      workflow.moduleViewsWithEdits
              .rxMap { 
                  case Left(module) => ModuleSummary(module)
                  case Right(edit) => TentativeSummary(edit)
                }
    )

  val artifactNodes = 
    workflow.moduleViewsWithEdits
            .allArtifacts
            .flatMap { x => x }
            .map { artifacts => 
              div(`class` := "the_artifacts",
                artifacts.map { case (name, artifact) => 
                  val icon = 
                    artifact.category match {
                      case ArtifactType.DATASET =>   "table"
                      case ArtifactType.FUNCTION =>  "code"
                      case ArtifactType.BLOB =>      "envelope-o"
                      case ArtifactType.FILE =>      "file"
                      case ArtifactType.PARAMETER => "sliders"
                      case ArtifactType.VEGALITE =>  "chart"
                      case _ =>                      "question"
                    }
                  div(`class` := "artifact",
                    span(`class` := "label",
                      FontAwesome(icon),
                      name
                    )
                  )
                }.toSeq
              )
            }
            .reactive

  val projectNameEditor = 
    Var[Option[dom.html.Input]](None)

  val root:Frag = 
    div(
      id := "table_of_contents", 
      `class` := "contents",
      div(`class` := "module_list",
        h3("Workflow"),
        moduleNodes.root
      ),
      div(`class` := "artifact_list",
        h3("Artifacts"),
        artifactNodes
      )
    )
}
