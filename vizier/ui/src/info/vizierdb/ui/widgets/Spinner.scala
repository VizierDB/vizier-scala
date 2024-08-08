/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
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
package info.vizierdb.ui.widgets

import scalatags.JsDom.all._
import org.scalajs.dom
import info.vizierdb.ui.rxExtras._
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import info.vizierdb.ui.Vizier

// Modeled after bootstrap
// https://getbootstrap.com/docs/4.3/components/spinners/
object Spinner
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  def apply(size: Int = 15): dom.Node =
    span(
      `class` := "spinner",
      width  := s"${size}px",
      height := s"${size}px",
      marginLeft := "auto",
      marginRight := "auto",
      display := "inline-block",
      css("animation-name") := "spinner-rotate",
      css("animation-duration") := "1s",
      css("animation-timing-function") := "linear",
      css("animation-delay") := "0s",
      css("animation-iteration-count") := "infinite",
      css("animation-direction") := "normal",
      css("animation-fill-mode") := "none",
      css("animation-play-state") := "running",
      border := ".25em solid black",
      borderRightColor := "transparent",
      borderRadius := "50%",
      ""
    ).render

  def lazyLoad(op: Future[dom.Node]): dom.Node =
  {
    val spinner = apply().render
    val body = span(`class` := "lazyload", spinner).render

    op.onComplete { 
      case Success(r) => body.replaceChild(r, spinner) 
      case Failure(err) => body.replaceChild(
          div(`class` := "error_result", err.toString()).render,
          spinner
        )
        Vizier.error(err.getMessage())
    }

    body
  }

}