package info.vizierdb.ui

import org.scalajs.dom.document
import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.URLDecoder
import play.api.libs.json._

import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.network.{ API, ClientURLs, BranchSubscription, SpreadsheetClient }
import info.vizierdb.ui.components.Project
import scala.util.{ Try, Success, Failure }
import info.vizierdb.util.Logging
import info.vizierdb.serialized.ProjectList
import info.vizierdb.serialized.PropertyList
import info.vizierdb.ui.components.dataset.Dataset
import info.vizierdb.ui.components.MenuBar
import info.vizierdb.ui.components.SettingsView
import info.vizierdb.ui.components.dataset.TableView
import info.vizierdb.nativeTypes
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.components.DisplayArtifact
import scala.concurrent.Future
import info.vizierdb.ui.components.StaticWorkflow


/**
 * Methods for initializing each Vizier interface and managing global state
 * 
 * This class serves two roles:
 * 1. It contains a set of methods allowing easy access to global state (e.g., api, links)
 * 2. It contains one method for each 'page' of the UI that connects to the API and sets up
 *    the DOM.
 */
@JSExportTopLevel("Vizier")
object Vizier
  extends Object
  with Logging
{
  implicit val ctx = Ctx.Owner.safe() 

  var api:API = null
  var links:ClientURLs = null

  @JSExport("init")
  def init(url: String = "http://localhost:5000/") =
  {
    api = API(url+"vizier-db/api/v1")
    links = ClientURLs(url)
  }

  lazy val arguments: Map[String, String] = 
    dom.window.location.search
       .substring(1)
       .split("&")
       .map { _.split("=").toSeq }
       .collect { 
          case Seq(k, v) => 
            URLDecoder.decode(k, "UTF-8") ->
              URLDecoder.decode(v, "UTF-8") 
        }
       .toMap

  val project = Var[Option[Project]](None)
  val menu = project.map { _.map { new MenuBar(_) } }

  def error(message: String) =
  {
    println(s"ERROR: $message")
    throw new Exception(message)
  }

  def main(args: Array[String]): Unit = 
  {

  }

  try {
    dom.experimental.Notification.requestPermission((x: String) => Unit)
  } catch {
    case t:Throwable => 
      println(s"Unable to initialize notifications: ${t.getMessage}")
  }
  
  @JSExport("project_view")
  def projectView(): Unit = 
  {
    val projectId = 
      arguments.get("project")
               .getOrElse { error("No Project ID specified") }
               .toLong
    val projectRequest = api.projectGet(projectId)
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      try {
        projectRequest
            .onComplete { 
              case Success(response) => 
                project() = Some(new Project(projectId).load(response))
                logger.debug(s"Project: ${project.now.get}")
                document.addEventListener("keydown", { (evt:dom.KeyboardEvent) => 
                  if(evt.key == "Enter" && evt.ctrlKey){
                    project.now.foreach { 
                      _.workflow.now.foreach {
                        _.moduleViewsWithEdits.saveAllCells()
                      }
                    }
                    evt.stopPropagation()
                  }
                })

                // The following bit can be uncommented for onLoad triggers
                // to automate development debugging
                // dom.window.setTimeout(
                //   () => {
                //     val workflow = 
                //       project.now
                //              .get
                //              .workflow
                //              .now
                //              .get
                //     val module = 
                //       workflow.moduleViewsWithEdits
                //               .appendTentative()
                //     // module.activeView.trigger { _ match {
                //     //   case Some(Left(commandlist)) =>
                //     //     commandlist.simulateClick("data", "load")
                //     //   case _ => 
                //     //     println("Waiting...")
                //     // }}
                //   },
                //   500
                // )

              case Failure(ex) => 
                error(ex.toString)
            }

        document.body.appendChild(
          Rx { project().map { _.root }
                        .getOrElse { Spinner(size = 30) } }.reactive
        )
        OnMount.trigger(document.body)
      } catch {
        case t: Throwable => logger.error(t.toString)
      }
    })
  }

  def createProject(name: String): Unit =
  {
    api.projectCreate(
      PropertyList(
        "name" -> JsString(name)
      )
    ) .onComplete {
        case Success(result) => 
          dom.window.location.href = s"project.html?project=${result.id}"
        case Failure(ex) =>
          error(ex.toString())
      }
  }

  @JSExport("project_list")
  def projectList(): Unit = 
  {
    val projectListRequest = 
      api.projectList()
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      var projects = Var[Option[ProjectList]](None)
      val projectNameField = Var[Option[dom.html.Input]](None)
      projectListRequest
        .onComplete {
          case Success(result) => 
            projects() = Some(result)
          case Failure(ex) =>
            error(ex.toString())
        }
      document.body.appendChild(
        div(id := "content",
            projects.map {
              case Some(ProjectList(projects)) => 
                div(`class` := "project_list_wrapper",
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
                                Some(input(`type` := "text", placeholder := "Project Name").render)
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
                              createProject(f.value)
                              projectNameField() = None
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
        )
      )
      OnMount.trigger(document.body)
    })
  }

  @JSExport("spreadsheet")
  def spreadsheet(): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val datasetId = arguments.get("dataset").get.toLong
    val cli = new SpreadsheetClient(projectId, datasetId, api)
    cli.connected.trigger { connected => 
      if(connected){ cli.subscribe(0) }
    }
    val table = new TableView(cli, 
        rowHeight = 30,
        maxHeight = 400,
        headerHeight = 40
    )
    cli.table = Some(table)

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild(table.root)
      OnMount.trigger(document.body)
    })
  }

  @JSExport("settings")
  def settings(): Unit =
  {
    val settings = new SettingsView()
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild(settings.root)
      OnMount.trigger(document.body)
    })
  }

  @JSExport("artifact")  
  def artifact(): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val artifactId = arguments.get("artifact").get.toLong
    val name = arguments.get("name")
    val artifact = api.artifactGet(projectId, artifactId, name = name)

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      val root = Var[Frag](div(`class` := "display_artifact", Spinner(50)))

      document.body.appendChild(root.reactive)
      OnMount.trigger(document.body)

      artifact.onComplete { 
        case Success(a) => root() = new DisplayArtifact(a).root
        case Failure(err) => Vizier.error(err.getMessage())
      }
    })
  }

  @JSExport("static_workflow")
  def staticWorkflow(): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val branchIdMaybe = arguments.get("branch").map { _.toLong }
    val workflowIdMaybe = arguments.get("workflow").map { _.toLong }

    val branchId = 
      branchIdMaybe.map { Future(_) }
                   .getOrElse { 
                       api.projectGet(projectId)
                          .map { _.defaultBranch }
                   }

    val workflowId =
      workflowIdMaybe.map { Future(_) }
                     .getOrElse { 
                       branchId.flatMap { Vizier.api.branchGet(projectId, _) }
                               .map { _.head.id }
                     }

    val workflow = 
      branchId.flatMap { b => 
        workflowId.flatMap { w => 
          println(s"Getting workflow: ($projectId, $b, $w)")
          api.workflowGet(projectId, b, w)
        }
      }

    document.addEventListener("DOMContentLoaded", { (e: dom.Event) =>
      val root = Var[Frag](div(`class` := "display_workflow", Spinner(50)))

      document.body.appendChild(root.reactive)
      OnMount.trigger(document.body)

      workflow.onComplete { 
        case Success(w) => 
          println(s"Got workflow: $w")
          root() = new StaticWorkflow(projectId, w).root
        case Failure(err) => Vizier.error(err.getMessage())
      }
    })
  }

}  
