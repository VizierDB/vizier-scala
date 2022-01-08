package info.vizierdb.ui.components.dataset

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.types._
import info.vizierdb.nativeTypes._
import info.vizierdb.ui.Vizier
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.ui.rxExtras.implicits._
import scala.util.Success
import scala.util.Failure

case class CaveatModal(
  projectId: Identifier, 
  datasetId: Identifier, 
  row: Option[String], 
  column: Option[Int]
) 
{
  implicit val ctx = Vizier.ctx
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val caveatsOrError = Var[Option[Either[Seq[Caveat],String]]](None)


  val modal = 
    div(
      div(
        `class` := "modal_background",
        onclick := { _:dom.Event => hide() }
      ),
      div(
        `class` := "caveat_modal",
        margin := "auto",
        Rx { 
          (caveatsOrError() match { 
            case Some(Left(caveats)) => 
              div(
                caveats.zipWithIndex.map { case (caveat, idx) => 
                  div(
                    `class` := s"caveat ${if(idx % 2 == 0){ "even_row" } else { "odd_row" }}",
                    caveat.message
                  )
                }:_*
              )
            case Some(Right(error)) => span(`class` := "error", error)
            case None => Spinner(50)
          }):Frag
        }
      )
    ).render

  Vizier.api.artifactGetAnnotations(
    projectId = projectId,
    artifactId = datasetId,
    column = column,
    row = row
  ).onComplete { 
    case Success(caveats) => caveatsOrError() = Some(Left(caveats))
    case Failure(e) => caveatsOrError() = Some(Right(e.getMessage))
  }

  def show()
  {
    dom.document.body.appendChild(modal)
  }

  def hide()
  {
    dom.document.body.removeChild(modal)
  }
}