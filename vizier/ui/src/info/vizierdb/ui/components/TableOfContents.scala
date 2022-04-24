package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.types._
import info.vizierdb.ui.Vizier

class TableOfContents(workflow: Workflow)
                     (implicit owner: Ctx.Owner)
{

  def LinkToModule(module: Module, body: Frag*): Frag =
    a(
        // The href exists for copyable links
      href := s"#${module.id_attr}", 

      // The onclick animates the scroll nicely
      onclick := { evt:dom.Event => 
        module.scrollIntoView
        false // prevent the default "href" behavior
      },

      body
    )

  def ModuleSummary(module: Module): Frag =
    Rx {
      val clazz = s"${module.subscription.state().toString.toLowerCase}_state"
      module.toc.map { toc => 
                      li(`class` := clazz + toc.titleLevel.map { " level_"+_ }.getOrElse { "" },
                        LinkToModule(module, toc.title),
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
                artifacts.map { case (name, (artifact, module)) => 
                  div(`class` := "artifact",
                    span(`class` := "label",
                      FontAwesome(ArtifactType.icon(artifact.category)),
                      name match {
                        case "" => s"Untitled ${artifact.category.toString.toLowerCase}"
                        case _ => name
                      },
                    ),
                    span(`class` := "spacer"),
                    span(`class` := "actions",
                      // Jump to Module
                      LinkToModule(module, FontAwesome("eye")),

                      // Category-specific actions
                      artifact.category match {
                        case ArtifactType.DATASET => 

                          // Spreadsheet view
                          Seq[Frag](
                            a(
                              href := Vizier.links.spreadsheet(workflow.project.projectId, artifact.id), 
                              target := "_blank",
                              FontAwesome("table")
                            )
                          )
                        case _ => Seq[Frag]()
                      },

                      // Download
                      a(
                        href := (
                          artifact.category match {
                            case ArtifactType.DATASET =>
                              Vizier.api.artifactGetCsvURL(
                                workflow.project.projectId,
                                artifact.id,
                              )
                            case ArtifactType.FILE =>
                              Vizier.api.artifactGetFileURL(
                                workflow.project.projectId,
                                artifact.id,
                              )
                            case _ => 
                              Vizier.api.artifactGetURL(
                                      workflow.project.projectId,
                                      artifact.id,
                              )
                          }
                        ),
                        target := "_blank",
                        FontAwesome("download"))
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
