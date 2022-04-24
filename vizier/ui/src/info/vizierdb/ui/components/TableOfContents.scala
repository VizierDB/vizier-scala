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
                      li(`class` := clazz + toc.titleLevel.map { " level_"+_ }.getOrElse { "" },
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

  def InspectorSummary(inspector: ArtifactInspector): Frag =
    li(`class` := "tentative", 
      inspector.selected
               .map { _ match {
                case None => span("Blank Inspector")
                case Some(name) => span(s"Inspect ${name}")
               }}
               .reactive
    )


  val moduleNodes =
    RxBufferView(ol(), 
      workflow.moduleViewsWithEdits
              .rxMap { 
                  case WorkflowModule(module) => ModuleSummary(module)
                  case WorkflowTentativeModule(edit) => TentativeSummary(edit)
                  case WorkflowArtifactInspector(inspector) => InspectorSummary(inspector)
                }
    )

  val artifactNodes = 
    workflow.moduleViewsWithEdits
            .allArtifacts
            .flatMap { x => x }
            .map { artifacts => 
              div(`class` := "the_artifacts",
                artifacts.map { case (name, (artifact, _)) => 
                  div(`class` := "artifact",
                    span(`class` := "label",
                      FontAwesome(ArtifactType.icon(artifact.category)),
                      name match {
                        case "" => s"Untitled ${artifact.category.toString.toLowerCase}"
                        case _ => name
                      }
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
