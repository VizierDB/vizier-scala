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
import java.time.ZonedDateTime
import info.vizierdb.util.{ StupidReactJsonMap, StupidReactJsonField }

case class ExportedProject(
  properties: Seq[StupidReactJsonField],
  defaultBranch: String,
  files: Seq[FileSummary],
  modules: Map[String, ExportedModule], 
  branches: Seq[ExportedBranch],
  createdAt: ZonedDateTime,
  lastModifiedAt: ZonedDateTime,
)
{
  lazy val propertyMap = 
    StupidReactJsonMap.decode(properties)
  lazy val name = 
    propertyMap.get("name")
               .map { _.as[String] }
               .getOrElse("Untitled Project")
  lazy val modulesByNewVizierId = 
    modules.values.map { module => 
      module.command.id.getOrElse { module.id } -> module
    }.toMap
}

object ExportedProject
{
  implicit val format: Format[ExportedProject] = Format(
    new Reads[ExportedProject] {
      def reads(j: JsValue): JsResult[ExportedProject] =
      {
        JsSuccess(
          ExportedProject(
            properties = (j \ "properties").as[Seq[StupidReactJsonField]],
            defaultBranch = (j \ "defaultBranch").as[String],
            files = (j \ "files").as[Seq[FileSummary]],
            modules = (j \ "modules").as[Map[String, ExportedModule]],
            branches = (j \ "branches").as[Seq[ExportedBranch]],
            createdAt = ExportedTimestamps.parseDate( (j \ "createdAt").as[String]),
            lastModifiedAt = ExportedTimestamps.parseDate( (j \ "lastModifiedAt").as[String])
          )
        )
      }
    },
    Json.writes
  )
}

