/* -- copyright-header:v1 --
 * Copyright (C) 2017-2020 University at Buffalo,
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
package info.vizierdb.catalog.serialized

import play.api.libs.json._
import info.vizierdb.util.HATEOAS
import info.vizierdb.types._

case class ModuleOutputDescription(
  stderr: Seq[JsValue],
  stdout: Seq[JsValue]
)

object ModuleOutputDescription
{
  implicit val format: Format[ModuleOutputDescription] = Json.format
}

case class ModuleDescription(
  id: String,
  state: Int,
  command: CommandDescription,
  text: String,
  timestamps: Timestamps,
  datasets: Seq[JsObject],
  charts: Seq[JsObject],
  artifacts: Seq[JsObject],
  outputs: ModuleOutputDescription,
  links: HATEOAS.T
)

object ModuleDescription
{
  implicit val format: Format[ModuleDescription] = Json.format
}

