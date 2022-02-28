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
import info.vizierdb.ui.network.{ API, BranchSubscription, SpreadsheetClient }
import info.vizierdb.ui.components.Project
import scala.util.{ Try, Success, Failure }
import info.vizierdb.util.Logging
import info.vizierdb.serialized.ProjectList
import info.vizierdb.serialized.PropertyList
import info.vizierdb.ui.components.dataset.Dataset
import info.vizierdb.ui.components.MenuBar
import info.vizierdb.ui.components.dataset.TableView
import info.vizierdb.nativeTypes

@JSExportTopLevel("Vizier")
object Vizier 
  extends Object
  with Logging
{
  implicit val ctx = Ctx.Owner.safe()
  // implicit val dataCtx = new Ctx.Data(new Rx.Dynamic[Int]( (owner, data) => 42, None ))

  lazy val api = API("http://localhost:5000/vizier-db/api/v1")

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
    throw new Exception(message)
  }

  def main(args: Array[String]): Unit = 
  {

  }

  dom.experimental.Notification.requestPermission((x: String) => Unit)
  
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
                project() = Some(new Project(projectId, api).load(response))
                logger.debug(s"Project: ${project.now.get}")

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
          div(`class` := "viewport",
            div(`class` := "content",
              Rx { project().map { _.root }
                            .getOrElse { div("loading...") } }.reactive
            )
          )
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
              case Some(ProjectList(projects, _)) => 
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
        rowDimensions = (780, 30),
        outerDimensions = ("800px", "400px"),
        headerHeight = 40
    )
    cli.table = Some(table)

    // val datasetFuture = 
    //   api.artifactGetDataset(
    //     projectId = projectId,
    //     artifactId = arguments.get("artifact").get.toLong,
    //     limit = Some(20),
    //   )
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.appendChild(table.root)
      OnMount.trigger(document.body)
    })
    //   document.body.style.background = "#ccf"
    //   datasetFuture.onComplete {
    //     case Failure(e) => error(e.toString())
    //     case Success(description) => 
    //     {
    //       val dataset = new Dataset(description, projectId)
    //       document.body.appendChild(dataset.root)

    //     }
    //     OnMount.trigger(document.body)
    //   }
    // })
  }

}  
