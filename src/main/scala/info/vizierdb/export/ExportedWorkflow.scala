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
import info.vizierdb.types._

case class ExportedWorkflow(
  id: String,
  createdAt: ZonedDateTime,
  action: String,
  packageId: Option[String],
  commandId: Option[String],
  actionModule: Option[String],
  modules: Seq[String]
)
{
  val decodedAction = ActionType.decode(action)
}


object ExportedWorkflow
{
  implicit val format: Format[ExportedWorkflow] = Format(
    new Reads[ExportedWorkflow] {
      def reads(j: JsValue): JsResult[ExportedWorkflow] =
      {
        JsSuccess(
          ExportedWorkflow(
            id = (j \ "id").as[String],
            createdAt = ExportedTimestamps.parseDate( (j \ "createdAt").as[String]),
            action = (j \ "action").as[String],
            packageId = (j \ "packageId").asOpt[String],
            commandId = (j \ "commandId").asOpt[String],
            actionModule = (j \ "actionModule").asOpt[String],
            modules = (j \ "modules").as[Seq[String]]
          )
        )
      }
    },
    Json.writes
  )
}

