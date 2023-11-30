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
import info.vizierdb.ui.network.BranchWatcherAPIProxy
import info.vizierdb.ui.network.SpreadsheetTools
import info.vizierdb.ui.widgets.Tooltip
import info.vizierdb.ui.widgets.SearchWidget

class TableOfContents(
  projectId: Identifier,
  modules: RxBuffer[WorkflowElement],
  artifacts: Rx[Map[String, (serialized.ArtifactSummary, WorkflowElement)]],
)(implicit owner: Ctx.Owner)
{

  def this(wf: Workflow)(implicit owner: Ctx.Owner)
  {
    this(
      wf.project.projectId,
      wf.moduleViewsWithEdits,
      wf.moduleViewsWithEdits.allArtifacts.map { x => x }
    )
  }

  val packages = 
    Seq[(String,String)](
      "Vizier" -> "https://github.com/VizierDB/vizier-scala/wiki",
      "Spark" -> "https://spark.apache.org/docs/3.3.1/sql-programming-guide.html",
      "Sedona" -> "https://sedona.apache.org/1.5.0/"
    )

  def LinkToModule(element: WorkflowElement, body: Frag*): Frag =
    a(
        // The href exists for copyable links
      href := s"#${element.id_attr}", 

      // The onclick animates the scroll nicely
      onclick := { evt:dom.Event => 
        element.scrollIntoView
        false // prevent the default "href" behavior
      },

      body
    )

  def ModuleSummary(module: Module): Frag =
    Rx {
      val clazz = s"${module.subscription.state().toString.toLowerCase}_state"
      val icon: Option[Frag] = 
        module.subscription.state() match {
          case ExecutionState.DONE      => None
          case ExecutionState.ERROR     => Some(FontAwesome("exclamation-triangle"))
          case ExecutionState.WAITING   => Some(FontAwesome("clock-o"))
          case ExecutionState.STALE     => Some(FontAwesome("clock-o"))
          case ExecutionState.CANCELLED => Some(FontAwesome("ban"))
          case ExecutionState.FROZEN    => Some(FontAwesome("snowflake-o"))
          case ExecutionState.RUNNING   => Some(FontAwesome("cogs"))
        }
      module.toc.map { toc => 
                      li(`class` := clazz + toc.titleLevel.map { " level_"+_ }.getOrElse { "" },
                        LinkToModule(module, toc.title),
                        icon.map { span(" (", _, ")") },
                        onmouseover := { _:dom.Event => module.highlight() = true },
                        onmouseout := { _:dom.Event => module.highlight() = false }
                      )
                    }
                    .getOrElse { 
                      li(
                        `class` := clazz,
                        s"${module.subscription.packageId}.${module.subscription.commandId}",
                        icon.map { span(" (", _, ")") },
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
                case module:Module => ModuleSummary(module)
                case edit:TentativeModule => TentativeSummary(edit)
                case inspector:ArtifactInspector => InspectorSummary(inspector)
                case _ => div().render
              }
    )

  def api:BranchWatcherAPIProxy =
    Vizier.project.now.get
          .branchSubscription.get
          .Client

  val artifactSearch = 
    SearchWidget("Search artifacts...")

  val visibleArtifacts:Rx[Iterable[(String, (serialized.ArtifactSummary, WorkflowElement))]] = 
    artifactSearch.filter(artifacts){ _._1.contains(_) }

  val artifactNodes = 
    visibleArtifacts.map { artifacts => 
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
                  .map { case (name, artifact, element) => 
                    val tooltip = span(name).render

                    div(`class` := "artifact",
                      onmouseover := { evt:dom.MouseEvent => 
                        if(name.length >= 12) { Tooltip.showSoon(evt)(tooltip) }
                        element match { 
                          case module:Module => module.highlight() = true
                          case _ => ()
                        }
                      },
                      onmouseout := { _:dom.Event => 
                        if(name.length >= 12) { Tooltip.hideSoon() }
                        element match { 
                          case module:Module => module.highlight() = false
                          case _ => ()
                        }
                      },
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
                                // href := Vizier.links.spreadsheet(projectId, artifact.id), 
                                target := "_blank",
                                FontAwesome("table"),
                                onclick := { _:dom.Event =>
                                  SpreadsheetTools.appendNewSpreadsheet(name)
                                }
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
                                  Some(name)
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
                        LinkToModule(element, FontAwesome("eye")),

                      )
                    )
                  }.toSeq,
              )
            }
            .reactive

  val documentationNodes = 
    ul(
      packages.map { case (name, link) =>
        li( FontAwesome("book"), a(href := link, name, target := "_blank") )
      }
    ).render

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
        artifactSearch.root,
        artifactNodes,
      ),
      div(`class` := "documentation_list",
        h3(`class` := "title", "Packages"),
        documentationNodes,
      )
    )
}
