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
package info.vizierdb.export

import play.api.libs.json._
import java.time.{ LocalDateTime, ZonedDateTime, ZoneId }
import java.time.format.DateTimeParseException

case class ExportedTimestamps(
  createdAt: ZonedDateTime,
  startedAt: Option[ZonedDateTime],
  finishedAt: Option[ZonedDateTime],
  lastModifiedAt: Option[ZonedDateTime]
)

object ExportedTimestamps
{
  def parseDate(dateStr: String): ZonedDateTime =
  {
    try { 
      ZonedDateTime.parse(dateStr)
    } catch {
      case _:DateTimeParseException => 
        ZonedDateTime.of(
          LocalDateTime.parse(dateStr),
          ZoneId.systemDefault()
        )
    }
  }

  implicit val format = Format[ExportedTimestamps](
    new Reads[ExportedTimestamps]{
      def reads(j: JsValue): JsResult[ExportedTimestamps] = 
        JsSuccess(
          ExportedTimestamps(
            createdAt = parseDate( (j \ "createdAt").as[String] ),
            startedAt = (j \ "startedAt").asOpt[String].map { parseDate(_) },
            finishedAt = (j \ "startedAt").asOpt[String].map { parseDate(_) },
            lastModifiedAt = (j \ "lastModifiedAt").asOpt[String].map { parseDate(_) },
          )
        )
    },
    Json.writes
  )
}

