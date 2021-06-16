package info.vizierdb.ui

import org.scalajs.dom.document
import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.URLDecoder

import rxExtras.implicits._
import rxExtras.RxBufferView
import state.BranchSubscription
import view.ProjectView
import scala.util.{ Try, Success, Failure }

object Vizier {
  import Ctx.Owner.Unsafe._
  implicit val dataCtx = new Ctx.Data(new Rx.Dynamic[Int]( (owner, data) => 42, None ))

  lazy val api = API("http://localhost:5000/vizier-db/api/v1")

  lazy val arguments: Map[String, String] = 
    dom.window.location.search
       .substring(1)
       .split("&")
       .map { _.split("=") }
       .map { x => URLDecoder.decode(x(0), "UTF-8") ->
                      URLDecoder.decode(x(1), "UTF-8") }
       .toMap

  lazy val project = Var[Option[ProjectView]](None)

  def error(message: String) =
    println(message)

  def main(args: Array[String]): Unit = 
  {
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      val projectId = 
        arguments.get("project")
                 .getOrElse { error("No Project ID specified"); return; }
      api.project(projectId)
            .onComplete { 
              case Success(response) => 
                project() = Some(new ProjectView(projectId).load(response))
                println(s"Project: ${project().get}")
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
    })
  }

}  
