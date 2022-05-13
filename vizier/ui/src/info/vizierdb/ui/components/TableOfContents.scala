package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome
import info.vizierdb.types._
import info.vizierdb.ui.Vizier
import info.vizierdb.util.StringUtils
import info.vizierdb.ui.components.dataset.CaveatModal
import info.vizierdb.ui.rxExtras.RxBuffer
import info.vizierdb.serialized

class TableOfContents(
  projectId: Identifier,
  modules: RxBuffer[WorkflowElement],
  artifacts: Rx[Map[String, (serialized.ArtifactSummary, Module)]],
)(implicit owner: Ctx.Owner)
{

  def this(wf: Workflow)(implicit owner: Ctx.Owner)
  {
    this(
      wf.project.projectId,
      wf.moduleViewsWithEdits,
      wf.moduleViewsWithEdits.allArtifacts.flatMap { x => x }
    )
  }

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
                case Left( (name, _) ) => span(s"Inspect ${name}")
                case Right(_) => span("Blank Inspector")
               }}
               .reactive
    )


  val moduleNodes =
    RxBufferView(ol(`class` := "the_modules"), 
      modules.rxMap { 
                case WorkflowModule(module) => ModuleSummary(module)
                case WorkflowTentativeModule(edit) => TentativeSummary(edit)
                case WorkflowArtifactInspector(inspector) => InspectorSummary(inspector)
              }
    )



  val artifactNodes = 
    artifacts.map { artifacts => 
              div(`class` := "the_artifacts",
                artifacts
                  .toSeq
                  .map { case (name, (artifact, module)) =>
                    (
                      name match {
                        case "" => s"Untitled ${artifact.category.toString.toLowerCase}"
                        case _ => name
                      },
                      artifact,
                      module
                    )
                  }
                  .sortBy { _._1 }
                  .map { case (name, artifact, module) => 
                    div(`class` := "artifact",
                      onmouseover := { _:dom.Event => module.highlight() = true },
                      onmouseout := { _:dom.Event => module.highlight() = false },
                      span(`class` := "label",
                        FontAwesome(ArtifactType.icon(artifact.category)),
                        StringUtils.ellipsize(name, 12)
                      ),
                      span(`class` := "spacer"),
                      span(`class` := "actions",
                        // Category-specific actions
                        artifact.category match {
                          case ArtifactType.DATASET => Seq[Frag](
                              // Caveat list
                              a(
                                href := Vizier.api.artifactDsGetAnnotationsURL(projectId, artifact.id),
                                onclick := { _:dom.Event =>
                                  CaveatModal(projectId, artifact.id,
                                    row = None,
                                    column = None
                                  ).show()
                                  /* return */ false // avoid link from triggering
                                },
                                FontAwesome("exclamation-triangle")
                              ),

                              // Spreadsheet view
                              a(
                                href := Vizier.links.spreadsheet(projectId, artifact.id), 
                                target := "_blank",
                                FontAwesome("table")
                              )
                            )

                          case ArtifactType.FUNCTION | ArtifactType.VEGALITE => Seq[Frag](
                              // "Pop-out"
                              a(
                                href := Vizier.links.artifact(projectId, artifact.id),
                                target := "_blank",
                                FontAwesome("share-square-o")
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
                                  projectId,
                                  artifact.id,
                                )
                              case ArtifactType.FILE =>
                                Vizier.api.artifactGetFileURL(
                                  projectId,
                                  artifact.id,
                                )
                              case _ => 
                                Vizier.api.artifactGetURL(
                                        projectId,
                                        artifact.id,
                                )
                            }
                          ),
                          target := "_blank",
                          FontAwesome("download")
                        ),

                        // Jump to Module
                        LinkToModule(module, FontAwesome("eye")),

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
        h3(`class` := "title", "Workflow"),
        moduleNodes.root
      ),
      div(`class` := "artifact_list",
        h3(`class` := "title", "Artifacts"),
        artifactNodes
      )
    )
}
