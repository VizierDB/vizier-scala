package info.vizierdb.ui

import org.scalajs.dom.document
import scalatags.JsDom.all._
import org.scalajs.dom
import rx._
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.URLDecoder

import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.rxExtras.RxBufferView
import info.vizierdb.ui.network.BranchSubscription
import info.vizierdb.ui.components.Project
import scala.util.{ Try, Success, Failure }

object Vizier {
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
    throw new Exception(message)

  def main(args: Array[String]): Unit = 
  {
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      try {
        val projectId = 
          arguments.get("project")
                   .getOrElse { error("No Project ID specified") }
        api.project(projectId)
              .onComplete { 
                case Success(response) => 
                  project() = Some(new Project(projectId, api).load(response))
                  println(s"Project: ${project.now.get}")
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
        case t: Throwable => println(t)
      }
    })
  }

}  
