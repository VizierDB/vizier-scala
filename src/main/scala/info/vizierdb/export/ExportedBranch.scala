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

case class ExportedBranch(
  id: String,
  createdAt: ZonedDateTime,
  lastModifiedAt: ZonedDateTime,
  sourceBranch: Option[String],
  sourceWorkflow: Option[String],
  sourceModule: Option[String],
  isDefault: Boolean,
  properties: Seq[StupidReactJsonField],
  workflows: Seq[ExportedWorkflow]
)
{
  lazy val propertyMap = 
    StupidReactJsonMap.decode(properties)
  lazy val name = 
    propertyMap.get("name")
               .map { _.as[String] }
               .getOrElse("Untitled Branch")  
}


object ExportedBranch
{
  implicit val format: Format[ExportedBranch] = Format(
    new Reads[ExportedBranch] {
      def reads(j: JsValue): JsResult[ExportedBranch] =
      {
        JsSuccess(
          ExportedBranch(
            id = (j \ "id").as[String],
            createdAt = ExportedTimestamps.parseDate( (j \ "createdAt").as[String]),
            lastModifiedAt = ExportedTimestamps.parseDate( (j \ "lastModifiedAt").as[String]),
            sourceBranch = (j \ "sourceBranch").asOpt[String],
            sourceWorkflow = (j \ "sourceWorkflow").asOpt[String],
            sourceModule = (j \ "sourceModule").asOpt[String],
            isDefault = (j \ "isDefault").as[Boolean],
            properties = (j \ "properties").as[Seq[StupidReactJsonField]],
            workflows = (j \ "workflows").as[Seq[ExportedWorkflow]]
          )
        )
      }
    },
    Json.writes
  )
}

