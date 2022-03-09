package info.vizierdb.ui.components.dataset

import org.scalajs.dom
import scalatags.JsDom.all._
import scalatags.JsDom.{ all => scalatags }
import info.vizierdb.nativeTypes.CellDataType
import play.api.libs.json._
import info.vizierdb.ui.widgets.Spinner

/**
 * Logic for rendering cell data values to dom nodes
 */
object RenderCell
{
  /**
   * Render the specified cell with the specified data type
   */
  def apply(
    value: JsValue, 
    dataType: CellDataType, 
    width: Int,
    caveatted: Option[dom.html.Button => Unit] = None,
    onclick: (dom.Event => Unit) = (_ => ())
  ): Frag =
  {
    div(
      `class` := (
        Seq("cell") ++ 
          (if(caveatted.isDefined) { Some("caveatted") } else { None }) ++
          (if(value == JsNull) { Some("null") } else { None })
      ).mkString(" "),
      css("width") := s"${width}px",
      css("height") := "100%",
      ((value, dataType) match {
        case (JsNull, _) => 
          span(" ")
        case (_, JsString("image/png")) => 
          img(src := "data:image/png;base64,"+value.as[String], height := "100%")
        case (_, JsString("string")) => 
          span(value.as[String])
        case _ => 
          span(value.toString())
      }),
      (if(caveatted.isDefined){
        val callback = caveatted.get
        val node:dom.html.Button = button(`class` := "show_caveat", "(?)").render
        node.onclick = { _:dom.Event => callback(node) }
        span(node)
      } else{span(`class` := "placeholder", visibility := "hidden")}),
      scalatags.onclick := onclick
    )
  }

  def header(
    name: String,
    dataType: CellDataType,
    width: Int,
  ): Frag =
  {
    div(
      `class` := "cell",
      css("width") := s"${width}px",
      span(
        `class` := "title",
        name
      ),
      span(
        `class` := "datatype",
        s" (${dataType match {
          case JsString(s) => s
          case _ => dataType.toString
        }})"
      ),
    )
  }

  def gutter(
    row: Long,
    width: Int,
    caveatted: Option[() => Unit] = None
  ): Frag =
    div(
      `class` := "gutter", 
      css("width") := s"${width}px",
      (caveatted match { 
        case None => span((row+1).toString)
        case Some(handler) => 
          a(
            `class` := "show_caveat",
            onclick := { _:dom.Event => handler() },
            (row+1).toString
          )
      })
    )

  def spinner(columnDataType: CellDataType) =
    div(
      textAlign := "center", 
      Spinner(15)
    )

}