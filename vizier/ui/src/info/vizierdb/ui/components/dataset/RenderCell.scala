package info.vizierdb.ui.components.dataset

import org.scalajs.dom
import scalatags.JsDom.all._
import info.vizierdb.nativeTypes.CellDataType
import play.api.libs.json._

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
    caveatted: Option[dom.html.Button => Unit] = None
  ): Frag =
  {
    td(
      `class` := (if(caveatted.isDefined) { "caveatted" } else { "cell" }),
      width := s"${defaultWidthForType(dataType)}px",
      (value match {
        case JsNull => "null"
        case _ => value.toString()
      }).toString,
      (if(caveatted.isDefined){
        val callback = caveatted.get
        val node:dom.html.Button = button(`class` := "show_caveat", "(?)").render
        node.onclick = { _:dom.Event => callback(node) }
        span(node)
      }else{span(`class` := "placeholder", visibility := "hidden")})
    )
  }

  def header(
    name: String,
    dataType: CellDataType
  ): Frag =
  {
    th(
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
      width := s"${defaultWidthForType(dataType)}px"
    )
  }

  def gutter(
    row: Long,
    caveatted: Option[() => Unit] = None
  ): Frag =
    td(
      `class` := "gutter", 
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

  /**
   * The default size for a column of this type in pixels
   */
  def defaultWidthForType(dataType: CellDataType):Int = 
    200

}