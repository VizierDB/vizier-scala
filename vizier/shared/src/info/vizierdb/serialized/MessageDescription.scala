/* -- copyright-header:v4 --
 * Copyright (C) 2017-2025 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology,
 *                         Breadcrumb Analytics.
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

import info.vizierdb.types.{ MessageType, StreamType }
import info.vizierdb.nativeTypes.JsValue

case class MessageDescription(
  `type`: MessageType.T,
  value: JsValue
)
{
  def t = `type`
  def withStream(stream: StreamType.T) =
    MessageDescriptionWithStream(
      `type` = `type`,
      value = value,
      stream = stream
    )
}

case class MessageDescriptionWithStream(
  `type`: MessageType.T,
  value: JsValue,
  stream: StreamType.T
)
{
  def t = `type`
  def removeType =
    MessageDescription(
      `type` = `type`,
      value = value
    )
}
