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

object ArtifactView
{
  implicit val ec: scala.concurrent.ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
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