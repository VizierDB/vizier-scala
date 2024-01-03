package info.vizierdb.ui.roots

import org.scalajs.dom
import org.scalajs.dom.document
import rx._
import info.vizierdb.ui.Vizier
import scalatags.JsDom.all._
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.components.DisplayArtifact
import scala.util.{ Try, Success, Failure }
import scala.concurrent.ExecutionContext.Implicits.global

object ArtifactView
{
  def apply(arguments: Map[String, String])(implicit owner: Ctx.Owner): Unit =
  {
    val projectId = arguments.get("project").get.toLong
    val artifactId = arguments.get("artifact").get.toLong
    val name = arguments.get("name")
    val artifact = Vizier.api.artifactGet(projectId, artifactId, name = name)

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

}