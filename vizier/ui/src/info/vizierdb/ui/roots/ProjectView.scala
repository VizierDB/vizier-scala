package info.vizierdb.ui.roots

import scalatags.JsDom.all._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.Vizier
import org.scalajs.dom
import org.scalajs.dom.document
import scala.util.{ Try, Success, Failure }
import info.vizierdb.ui.components.Project
import info.vizierdb.util.Logging
import info.vizierdb.ui.widgets.Spinner
import scala.concurrent.ExecutionContext.Implicits.global
import rx._
import info.vizierdb.ui.rxExtras.OnMount

object ProjectView
  extends Logging
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectId = 
      arguments.get("project")
               .getOrElse { Vizier.error("No Project ID specified") }
               .toLong
    val projectRequest = Vizier.api.projectGet(projectId)
    document.addEventListener("DOMContentLoaded", { (e: dom.Event) => 
      try {
        projectRequest
            .onComplete { 
              case Success(response) => 
                Vizier.project() = Some(new Project(projectId).load(response))
                logger.debug(s"Project: ${Vizier.project.now.get}")
                document.addEventListener("keydown", { (evt:dom.KeyboardEvent) => 
                  if(evt.key == "Enter" && evt.ctrlKey){
                    Vizier.project.now.foreach { 
                      _.workflow.now.foreach {
                        _.moduleViewsWithEdits.saveAllCells()
                      }
                    }
                    evt.stopPropagation()
                  } else if (evt.keyCode == 116 /* f5 */) {
                    // disable reload https://github.com/VizierDB/vizier-scala/issues/159
                    evt.preventDefault()
                  // } else {
                  //   println(s"KEY: ${evt.keyCode}")
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
                //               .prependTentative()
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
                Vizier.error(ex.toString)
            }

        document.body.appendChild(
          Rx { Vizier.project().map { _.root }
                        .getOrElse { Spinner(size = 30) } }.reactive
        )
        OnMount.trigger(document.body)
      } catch {
        case t: Throwable => logger.error(t.toString)
      }
    })
  }
}