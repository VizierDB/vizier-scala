/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.ui.components.dataset

import org.scalajs.dom
import scalatags.JsDom.all._
import scalatags.JsDom.{ all => scalatags }
import info.vizierdb.nativeTypes.CellDataType
import play.api.libs.json._
import info.vizierdb.ui.widgets.Spinner
import info.vizierdb.serialized
import info.vizierdb.serializers.mlvectorFormat
import info.vizierdb.ui.widgets.Tooltip
import info.vizierdb.util.StringUtils

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
    position: Int,
    caveatted: Option[dom.html.Button => Unit] = None,
    onclick: (dom.Event => Unit) = (_ => ())
  ): Frag =
  {

    def makeTextCell(content: String): Frag =
      if(content.size > 23){
        span(
          StringUtils.ellipsize(content, 20),
          Tooltip(
            div(`class` := "tooltip_text", content)
          )
        )
      } else { span(content) }

    div(
      `class` := (
        Seq("cell") ++ 
          (if(caveatted.isDefined) { Some("caveatted") } else { None }) ++
          (if(value == JsNull) { Some("null") } else { None })
      ).mkString(" "),
      css("width") := s"${width}px",
      css("left") := s"${position}px",
      css("height") := "100%",
      ((value, dataType) match {
        case (JsNull, _) => 
          span(" ")
        case (_, JsString("image/png")) => 
          {
            img(
              `class` := "table_image", 
              src := "data:image/png;base64,"+value.as[String], 
              Tooltip(
                img(`class` := "tooltip_image",
                    src := "data:image/png;base64,"+value.as[String],
                )
              )
            ),
          }
        case (_, JsString("vector")) => 
          {
            value.as[serialized.MLVector].show(5)
          }
        case (_, JsString("string")) => 
          makeTextCell(value.as[String])
        case _ => 
          makeTextCell(value.toString())
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
    position: Int,
  ): Frag =
  {
    div(
      `class` := "cell",
      css("width") := s"${width}px",
      css("left") := s"${position}px",
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