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
package info.vizierdb.export

import play.api.libs.json._

case class ExportedCommand(
  id: Option[String],
  packageId: String,
  commandId: String,
  arguments: JsValue,
  revisionOfId: Option[String],
  properties: Option[Map[String,JsValue]]
)
{
  lazy val (sanitizedPackageId, sanitizedCommandId) = 
    (packageId, commandId) match {
      case ("python", "code") => ("script", "python")
      case ("markdown", "code") => ("docs", "markdown")
      case ("sampling", x) => ("sample", x)
      case x => x
    }

}


object ExportedCommand
{
  implicit val format: Format[ExportedCommand] = Json.format
}

