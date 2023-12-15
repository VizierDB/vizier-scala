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
import info.vizierdb.ui.widgets.ShowModal

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


  val root = 
    div(
      `class` := "caveat_modal",
      margin := "auto",
      caveatsOrError.map { 
        case Some(Left(caveats)) => 
          div(
            caveats.zipWithIndex.map { case (caveat, idx) => 
              div(
                `class` := s"caveat ${if(idx % 2 == 0){ "even_row" } else { "odd_row" }}",
                caveat.message
              )
            }:_*
          ):Frag
        case Some(Right(error)) => span(`class` := "error", error):Frag
        case None => Spinner(50):Frag
      }.reactive
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

  def show() = ShowModal.acknowledge(root)
}