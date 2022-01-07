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
    caveatted: Boolean = false
  ): Frag =
  {
    td(
      `class` := (if(caveatted) { "caveatted" } else { "cell" }),
      width := s"${defaultWidthForType(dataType)}px",
      (value match {
        case JsNull => "null"
        case _ => value.toString()
      }).toString
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

  /**
   * The default size for a column of this type in pixels
   */
  def defaultWidthForType(dataType: CellDataType):Int = 
    200

}