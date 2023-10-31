package info.vizierdb.ui.components

import rx._
import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.serialized.ProjectList
import scala.util.{ Success, Failure }
import info.vizierdb.ui.Vizier
import scala.concurrent.ExecutionContext.Implicits.global
import info.vizierdb.nativeTypes
import info.vizierdb.serialized.PropertyList
import play.api.libs.json._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.widgets.FontAwesome

class ProjectListView()(implicit owner: Ctx.Owner)
{
  val projects = Var[Option[ProjectList]](None)
  val projectNameField = Var[Option[dom.html.Input]](None)
  loadProjectList()
  
  def loadProjectList(): Unit =
  {
    Vizier.api.projectList()
      .onComplete {
        case Success(result) => 
          projects() = Some(result)
        case Failure(ex) =>
          Vizier.error(ex.toString())
      }
  }


  def createProject(name: String): Unit =
  {
    Vizier.api.projectCreate(
      PropertyList(
        "name" -> JsString(name)
      )
    ) .onComplete {
        case Success(result) => 
          dom.window.location.href = s"project.html?project=${result.id}"
        case Failure(ex) =>
          Vizier.error(ex.toString())
      }
  }

  val root =
    div(id := "project_list_content",
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
                    ),
                    span(
                      `class` := "actions",
                      a(
                        FontAwesome("trash-o"),
                        href := "#",
                        onclick := { _:dom.Event =>
                          val name = projectRef("name").getOrElse { s"Untitled Project ${idx}" }
                          if( dom.window.confirm(s"Really delete '$name'?") ){
                            Vizier.api.projectDelete(projectRef.id)
                                .onComplete { 
                                  case Success(_) => loadProjectList()
                                  case Failure(ex) => Vizier.error(ex.toString())
                                }
                          }
                        }
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
    ).render
}