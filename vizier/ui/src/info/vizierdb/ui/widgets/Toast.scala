package info.vizierdb.ui.widgets

import scala.scalajs.js.annotation._
import scalatags.JsDom.all._
import org.scalajs.dom

object Toast
{
  def apply(
    text: String, 
    level: String = "error", 
    summaryWidth: Int = 100,
    timeoutMs: Long = 10000
  ): dom.html.Element =
  {
    val lines = text.split("\n")
    val lead = lines.head
    val rest = lines.tail

    val node = div(`class` := s"toast $level",
      (if(lead.length <= summaryWidth){
        div(`class` := "summary", lead)
      } else {
        div(
          span(`class` := "summary",
            lead.take(summaryWidth-3)
          ),
          span(`class` := "summary_only", "..."),
          span(`class` := "detail", lead.drop(summaryWidth-3))
        )
      }),
      rest.map { div(`class` := "detail", _) }
    ).render
    
    dom.document.body.appendChild(node)

    dom.window.setTimeout(
      { () => 
        dom.document.body.removeChild(node)
      },
      timeoutMs
    )

    node
  }
}