package info.vizierdb.ui.components

import org.scalajs.dom
import scalatags.JsDom.all._
import rx._
import info.vizierdb.ui.rxExtras.implicits._
import info.vizierdb.serialized.ArtifactSummary
import info.vizierdb.types._
import info.vizierdb.ui.widgets.FontAwesome

/**
 * A user interface widget to help users to inspect the contents of artifacts.  These are
 * created by [[Module]] and typically attached to one as well.
 */
class ArtifactInspector(
  var position: Int,
  val visibleArtifacts: Var[Rx[Map[String, (ArtifactSummary, Module)]]],
  val editList: TentativeEdits
)(implicit owner: Ctx.Owner)
{
  val selected = Var[Option[String]](None)

  val summary: Rx[Option[ArtifactSummary]] = Rx {
    selected().flatMap { name =>
      val a = visibleArtifacts() 
      val b = a()
      b.get(name).map { _._1 }
    }
  }

  var nowShowing:Option[Identifier] = None
  val container = div(span()).render

  summary.trigger { s => 
    if(s.map { _.id } != nowShowing){ 
      val data = div(s"Showing $s").render
      container.replaceChild(data, container.lastChild)
    }
  }

  val root = 
    div(
      `class` := "module inspector", 
      div(
        `class` := "menu",
        button(
          FontAwesome("trash"),
          onclick := { _:dom.Event => editList.dropInspector(this) }
        ),
        div(`class` := "spacer")
      ),
      visibleArtifacts.flatMap { _.map { m => 
        div(
          `class` := "artifact_picker",
          m.map { case (name, (summary, _)) =>
            div(`class` := "option", 
              FontAwesome(ArtifactType.icon(summary.category)),
              name,
              onclick := { _:dom.Event => selected() = Some(name) }
            )
          }.toSeq
        ) 
      }}.reactive,
      container
    ).render
}