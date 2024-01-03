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
package info.vizierdb.ui.components

import rx._
import org.scalajs.dom
import scalatags.JsDom.all._
import scala.scalajs.js

import info.vizierdb.serialized
import info.vizierdb.types._
import info.vizierdb.ui.rxExtras.OnMount
import info.vizierdb.ui.facades.VegaEmbed
import info.vizierdb.serializers.playToNativeJson
import info.vizierdb.ui.components.dataset.Dataset
import info.vizierdb.serializers._

/**
 * A module to display artifacts
 * 
 * Most of the logic here overlaps with Message... many artifact types are also message types
 * It might be convenient to find a way to share code between the two
 */
class DisplayArtifact(description: serialized.ArtifactDescription)(implicit owner: Ctx.Owner)
{
  val root = div(
    `class` := s"artifact ${description.t.toString.toLowerCase}",
    (description, description.t) match {
      case (j:serialized.JsonArtifactDescription, ArtifactType.VEGALITE) =>
        val divId = DisplayArtifact.nextId
        div(
          OnMount { node => 
            VegaEmbed(
              s"#$divId", 
              playToNativeJson(j.payload).asInstanceOf[js.Dictionary[Any]]
            )
          },
          id := divId,
        )
      case (j:serialized.JsonArtifactDescription, ArtifactType.FUNCTION) => 
        pre(
          j.payload.as[String]
        )
      case (j:serialized.JsonArtifactDescription, ArtifactType.PARAMETER) => 
        pre(
          j.payload.as[serialized.ParameterArtifact].nativeValue.toString()
        )
      case (d:serialized.DatasetDescription, _) =>
        new Dataset(d).root
      case _ =>
        span(s"Unsupported artifact type: ${description.t}")
    }
  ).render
}

object DisplayArtifact
{
  var id = -1

  def nextId = s"display_artifact_${id += 1}"
}

