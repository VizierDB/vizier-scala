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
package info.vizierdb.serialized

import info.vizierdb.nativeTypes.{ DateTime, dateDiffMillis, formatDate }
import java.time.temporal.ChronoUnit
import info.vizierdb.util.StringUtils

case class Timestamps(
  createdAt: DateTime,
  startedAt: Option[DateTime],
  finishedAt: Option[DateTime]
)
{
  def runtimeMillis: Option[Long] = 
    finishedAt.flatMap { end =>
      startedAt.map { start =>
        dateDiffMillis(start, end)
      }
    }

  def runtimeString = 
    runtimeMillis.map { StringUtils.formatDuration(_) }
                 .getOrElse { "Unknown" }

  def createdAtString = 
    formatDate(createdAt)

  def startedAtString = 
    startedAt.map { formatDate(_) }.getOrElse { "Unknown" }

  def finishedAtString = 
    finishedAt.map { formatDate(_) }.getOrElse { "Unknown" }
}

object Timestamps
{
  def apply(createdAt: DateTime, startedAt: DateTime = null, finishedAt: DateTime = null): Timestamps =
    Timestamps(
      createdAt = createdAt,
      startedAt = Option(startedAt),
      finishedAt = Option(finishedAt)
    )
}
