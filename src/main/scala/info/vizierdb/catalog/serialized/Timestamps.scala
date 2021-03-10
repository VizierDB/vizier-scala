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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

case class Timestamps(
  createdAt: String,
  startedAt: Option[String],
  finishedAt: Option[String]
)

object Timestamps
{
  implicit val format: Format[Timestamps] = Json.format

  def format(t: ZonedDateTime): String =
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(t)

  def apply(
    createdAt: ZonedDateTime,
    startedAt: Option[ZonedDateTime] = None,
    finishedAt: Option[ZonedDateTime] = None,
  ): Timestamps =
    Timestamps(
      createdAt  = format(createdAt),
      startedAt  = startedAt.map { format(_) },
      finishedAt = finishedAt.map { format(_) }
    )
}

