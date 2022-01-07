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
import info.vizierdb.ui.network.{ API, BranchSubscription }
import info.vizierdb.ui.components.Project
import scala.util.{ Try, Success, Failure }
import info.vizierdb.util.Logging
import info.vizierdb.serialized.ProjectList
import info.vizierdb.serialized.PropertyList
import info.vizierdb.ui.components.dataset.Dataset

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

  def error(message: String) =
  {
    throw new Exception(message)
  }

  def main(args: Array[String]): Unit = 
  {

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
                project() = Some(new Project(projectId, api).load(response))
                logger.debug(s"Project: ${project.now.get}")
              case Failure(ex) => 
                error(ex.toString)
            }

        document.body.appendChild(
          div(id := "content",
            tag("nav")(
              ul(id := "main_menu", `class` := "menu",
                li("menu 1", ul(
                  li("menu item 1.1"),
                  li("menu item 1.2"),
                )),
                li("menu 2", ul(
                  li("menu item 2.1"),
                ))
              )
            ),
            Rx { project().map { _.root }
                          .getOrElse { div("loading...") } }
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
      var projectNameField = Var[Option[dom.Node]](None)
      projectListRequest
        .onComplete {
          case Success(result) => 
            projects() = Some(result)
          case Failure(ex) =>
            error(ex.toString())
        }
      document.body.appendChild(
        div(id := "content",
            Rx { projects
              projects() match {
                case Some(ProjectList(projects, _)) => 
                  div(
                    ul(
                      projects.map { projectRef =>
                        li(
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
                            span(
                              `class` := "project_modified",
                              s"(${projectRef.lastModifiedAt.toLocaleDateString()} ${projectRef.lastModifiedAt.toLocaleTimeString()})"
                            )
                          )
                        )
                      }
                    ),
                    div(
                      Rx { 
                        projectNameField() match {
                          case None => span():dom.Node
                          case Some(f) => f
                        }
                      },
                      button(
                        onclick := { (_:dom.MouseEvent) => 
                          projectNameField.now match {
                            case None => projectNameField() = Some(input(`type` := "text"):dom.Node)
                            case Some(nameField) => 
                              createProject(projectNameField.now.get.asInstanceOf[dom.html.Input].value)
                              projectNameField() = None
                          }
                        },
                        "Create Project"
                      )
                    )
                  )
                case None => 
                  div("Loading project list...")
              }
            }
        )
      )
      OnMount.trigger(document.body)
    })
  }

  @JSExport("spreadsheet")
  def spreadsheet(): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val datasetFuture = 
      api.artifactGetDataset(
        projectId = projectId,
        artifactId = arguments.get("artifact").get.toLong,
        limit = Some(20),
      )
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      document.body.style.background = "#ccf"
      datasetFuture.onComplete {
        case Failure(e) => error(e.toString())
        case Success(description) => 
        {
          val dataset = new Dataset(description, projectId)
          document.body.appendChild(dataset.root)

        }
        OnMount.trigger(document.body)
      }
    })
  }

}  
